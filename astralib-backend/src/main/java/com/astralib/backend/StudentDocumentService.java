package com.astralib.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Manages the lifecycle of private student document uploads.
 *
 * Enforces:
 * - Strict JWT user-ownership scoping.
 * - Validation of file format, magic bytes, and size (up to 50MB).
 * - Safe server-side storage outside public web roots.
 * - Text extraction across PDF, DOCX, and TXT.
 * - Chunking and vector embedding generation.
 * - Complete isolation from the public catalogue.
 */
@Service
public class StudentDocumentService {

    private static final Logger logger = LoggerFactory.getLogger(StudentDocumentService.class);

    private static final long MAX_FILE_SIZE = 50L * 1024L * 1024L; // 50 MB
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Value("${astralib.data-root:../data}")
    private String dataRoot;

    @Autowired
    private StudentDocumentRepository studentDocumentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DocumentExtractionService documentExtractionService;

    @Autowired
    private DocumentChunkerService documentChunkerService;

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private VectorRecordRepository vectorRecordRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public record DocumentResponse(
            Long id,
            Long userId,
            String originalFilename,
            String fileFormat,
            Long fileSize,
            String status,
            Integer chunkCount,
            String errorMessage,
            String createdAt
    ) {
    }

    public static class DocumentNotFoundException extends RuntimeException {
        public DocumentNotFoundException(Long id) {
            super("Document not found: " + id);
        }
    }

    public static class DocumentValidationException extends RuntimeException {
        public DocumentValidationException(String message) {
            super(message);
        }
    }

    /**
     * Uploads and stores a student document, then immediately initiates
     * extraction, chunking, and embedding.
     */
    @Transactional
    public DocumentResponse uploadDocument(Long userId, MultipartFile file) {
        if (userId == null) {
            throw new DocumentValidationException("Authenticated user ID is required.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new DocumentValidationException("User not found: " + userId));

        if (file == null || file.isEmpty()) {
            throw new DocumentValidationException("Uploaded file cannot be empty.");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new DocumentValidationException("File size exceeds maximum allowed limit of 50 MB.");
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            originalName = "document";
        }
        originalName = originalName.trim();

        String format = detectAndValidateFormat(file, originalName);

        // Save file to secure user directory
        Path storageDir = getStorageDirectory(userId);
        try {
            Files.createDirectories(storageDir);
        } catch (IOException e) {
            throw new RuntimeException("Could not create storage directory for user documents.", e);
        }

        String safeStoredName = UUID.randomUUID() + "_" + sanitizeFilename(originalName);
        Path targetPath = storageDir.resolve(safeStoredName);

        try {
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to store uploaded file on server.", e);
        }

        StudentDocument document = new StudentDocument();
        document.setUser(user);
        document.setOriginalFilename(originalName);
        document.setStoredFilePath(targetPath.toAbsolutePath().toString());
        document.setFileFormat(format);
        document.setFileSize(file.getSize());
        document.setStatus(StudentDocument.STATUS_PENDING);
        document.setChunkCount(0);

        studentDocumentRepository.save(document);

        // Process the document (extract, chunk, embed)
        processDocument(document);

        return toResponse(document);
    }

    /**
     * Synchronously processes an uploaded document:
     * PENDING -> PROCESSING -> READY (or FAILED).
     */
    public void processDocument(StudentDocument document) {
        document.setStatus(StudentDocument.STATUS_PROCESSING);
        studentDocumentRepository.save(document);

        File localFile = new File(document.getStoredFilePath());
        if (!localFile.exists() || !localFile.isFile()) {
            document.setStatus(StudentDocument.STATUS_FAILED);
            document.setErrorMessage("Stored document file was not found on disk.");
            studentDocumentRepository.save(document);
            return;
        }

        try {
            List<ExtractedSection> sections = documentExtractionService.extractSections(localFile);
            if (sections.isEmpty()) {
                document.setStatus(StudentDocument.STATUS_FAILED);
                document.setErrorMessage("No readable text or sections could be extracted from this document.");
                studentDocumentRepository.save(document);
                return;
            }

            String docId = "STUDENT-DOC-" + document.getId();
            List<DocumentChunkerService.Chunk> allChunks = new ArrayList<>();
            int chunkIndex = 0;

            for (ExtractedSection section : sections) {
                if (section.getContent() == null || section.getContent().isBlank()) {
                    continue;
                }

                List<DocumentChunkerService.Chunk> sectionChunks = documentChunkerService.chunkTextWithSection(
                        docId,
                        document.getOriginalFilename(),
                        "Student Upload",
                        "Student Document",
                        "student_document",
                        section.getTitle(),
                        section.getContent(),
                        chunkIndex
                );

                allChunks.addAll(sectionChunks);
                chunkIndex += sectionChunks.size();
            }

            if (allChunks.isEmpty()) {
                document.setStatus(StudentDocument.STATUS_FAILED);
                document.setErrorMessage("Document produced no valid text chunks.");
                studentDocumentRepository.save(document);
                return;
            }

            // Clean any previous vector records for this document
            vectorRecordRepository.deleteByDocumentId(docId);

            List<VectorRecord> vectorRecords = new ArrayList<>();
            for (int i = 0; i < allChunks.size(); i++) {
                DocumentChunkerService.Chunk chunk = allChunks.get(i);
                float[] embedding = embeddingService.generateEmbedding(chunk.getContent());
                String embeddingJson = objectMapper.writeValueAsString(embedding);

                VectorRecord record = new VectorRecord(
                        chunk.getChunkId(),
                        chunk.getDocumentId(),
                        "student_document",
                        chunk.getTitle(),
                        chunk.getAuthor(),
                        chunk.getCategory(),
                        chunk.getSection(),
                        i,
                        chunk.getContent(),
                        embeddingJson
                );
                vectorRecords.add(record);
            }

            vectorRecordRepository.saveAll(vectorRecords);

            document.setStatus(StudentDocument.STATUS_READY);
            document.setChunkCount(vectorRecords.size());
            document.setErrorMessage(null);
            studentDocumentRepository.save(document);

            logger.info("Successfully processed student document id={} with {} chunks.", document.getId(), vectorRecords.size());

        } catch (Exception e) {
            logger.error("Processing failed for student document id={}: {}", document.getId(), e.getMessage(), e);
            document.setStatus(StudentDocument.STATUS_FAILED);
            document.setErrorMessage("Processing failed: " + e.getMessage());
            studentDocumentRepository.save(document);
        }
    }

    /**
     * Lists all documents belonging to the authenticated user.
     */
    @Transactional(readOnly = true)
    public List<DocumentResponse> listDocuments(Long userId) {
        if (userId == null) {
            return List.of();
        }

        List<StudentDocument> docs = studentDocumentRepository.findByUser_IdOrderByCreatedAtDesc(userId);
        List<DocumentResponse> responses = new ArrayList<>(docs.size());
        for (StudentDocument doc : docs) {
            responses.add(toResponse(doc));
        }
        return responses;
    }

    /**
     * Gets a single document belonging to the authenticated user.
     */
    @Transactional(readOnly = true)
    public DocumentResponse getDocument(Long userId, Long documentId) {
        StudentDocument doc = owned(userId, documentId);
        return toResponse(doc);
    }

    /**
     * Deletes a student document, its vector records, and its file on disk.
     */
    @Transactional
    public void deleteDocument(Long userId, Long documentId) {
        StudentDocument doc = owned(userId, documentId);

        // Delete vector records
        String docId = "STUDENT-DOC-" + doc.getId();
        try {
            vectorRecordRepository.deleteByDocumentId(docId);
        } catch (Exception e) {
            logger.warn("Error deleting vectors for document {}: {}", docId, e.getMessage());
        }

        // Delete local file
        if (doc.getStoredFilePath() != null) {
            try {
                Path path = Paths.get(doc.getStoredFilePath());
                Files.deleteIfExists(path);
            } catch (Exception e) {
                logger.warn("Could not delete stored file on disk: {}", doc.getStoredFilePath());
            }
        }

        // Delete database record
        studentDocumentRepository.delete(doc);
        logger.info("Deleted student document id={} for user id={}", documentId, userId);
    }

    private StudentDocument owned(Long userId, Long documentId) {
        if (userId == null) {
            throw new DocumentValidationException("Authentication required.");
        }
        return studentDocumentRepository.findByIdAndUser_Id(documentId, userId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));
    }

    private Path getStorageDirectory(Long userId) {
        return Paths.get(dataRoot, "student_documents", "user_" + userId);
    }

    private String sanitizeFilename(String filename) {
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /**
     * Validates both filename extension and actual file contents (magic bytes/structure).
     */
    public String detectAndValidateFormat(MultipartFile file, String filename) {
        String ext = "";
        int dot = filename.lastIndexOf('.');
        if (dot >= 0 && dot < filename.length() - 1) {
            ext = filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        }

        if (!List.of("pdf", "docx", "txt").contains(ext)) {
            throw new DocumentValidationException("Unsupported file format '." + ext + "'. Only PDF, DOCX, and TXT files are supported.");
        }

        byte[] header = new byte[8];
        try (InputStream is = file.getInputStream()) {
            int read = is.read(header);
            if (read <= 0) {
                throw new DocumentValidationException("File is empty.");
            }
        } catch (IOException e) {
            throw new DocumentValidationException("Unable to read file content for validation.");
        }

        if ("pdf".equals(ext)) {
            // PDF must start with %PDF- (0x25, 0x50, 0x44, 0x46, 0x2D)
            if (header.length < 5 ||
                    header[0] != 0x25 || header[1] != 0x50 ||
                    header[2] != 0x44 || header[3] != 0x46 || header[4] != 0x2D) {
                throw new DocumentValidationException("Invalid PDF file: Missing %PDF- file signature.");
            }
            return "PDF";
        }

        if ("docx".equals(ext)) {
            // DOCX is a ZIP file starting with PK (0x50, 0x4B, 0x03, 0x04)
            if (header.length < 4 ||
                    header[0] != 0x50 || header[1] != 0x4B ||
                    header[2] != 0x03 || header[3] != 0x04) {
                throw new DocumentValidationException("Invalid DOCX file: Missing ZIP/OOXML file signature.");
            }
            // Verify it is a valid OPCPackage
            try (InputStream is = file.getInputStream()) {
                try (OPCPackage pkg = OPCPackage.open(is)) {
                    if (pkg.getParts().isEmpty()) {
                        throw new DocumentValidationException("Malformed DOCX file: Empty package.");
                    }
                }
            } catch (Exception e) {
                throw new DocumentValidationException("Malformed or corrupted DOCX file: " + e.getMessage());
            }
            return "DOCX";
        }

        if ("txt".equals(ext)) {
            // Check that the file is not binary
            try (InputStream is = file.getInputStream()) {
                byte[] sample = new byte[1024];
                int read = is.read(sample);
                for (int i = 0; i < read; i++) {
                    byte b = sample[i];
                    // Check for null byte, which indicates binary data
                    if (b == 0x00) {
                        throw new DocumentValidationException("Invalid TXT file: File contains binary content.");
                    }
                }
            } catch (IOException e) {
                throw new DocumentValidationException("Unable to read TXT file content for validation.");
            }
            return "TXT";
        }

        throw new DocumentValidationException("Unsupported file type: " + ext);
    }

    private DocumentResponse toResponse(StudentDocument doc) {
        return new DocumentResponse(
                doc.getId(),
                doc.getUser() != null ? doc.getUser().getId() : null,
                doc.getOriginalFilename(),
                doc.getFileFormat(),
                doc.getFileSize(),
                doc.getStatus(),
                doc.getChunkCount(),
                doc.getErrorMessage(),
                doc.getCreatedAt() != null ? doc.getCreatedAt().format(ISO_FORMATTER) : null
        );
    }
}
