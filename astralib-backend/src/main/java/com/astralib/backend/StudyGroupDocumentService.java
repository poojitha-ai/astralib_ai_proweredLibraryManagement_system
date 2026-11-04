
package com.astralib.backend;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class StudyGroupDocumentService {

    private static final long MAX_FILE_SIZE = 50L * 1024L * 1024L;

    private final StudyGroupDocumentRepository documentRepository;
    private final StudyGroupRepository groupRepository;
    private final StudyGroupMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final StudentDocumentService validator;
    private final Path dataRoot;

    public StudyGroupDocumentService(
            StudyGroupDocumentRepository documentRepository,
            StudyGroupRepository groupRepository,
            StudyGroupMemberRepository memberRepository,
            UserRepository userRepository,
            StudentDocumentService validator,
            @Value("${astralib.data-root:../data}") String dataRoot
    ) {
        this.documentRepository = documentRepository;
        this.groupRepository = groupRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.validator = validator;
        this.dataRoot = Paths.get(dataRoot).toAbsolutePath().normalize();
    }

    public record DocumentResponse(
            Long id,
            Long studyGroupId,
            Long uploadedBy,
            String uploaderName,
            String originalFilename,
            String fileFormat,
            Long fileSize,
            String createdAt
    ) {}

    public record DownloadFile(
            Resource resource,
            String filename,
            String fileFormat,
            Long fileSize
    ) {}

    @Transactional
    public DocumentResponse upload(
            Long groupId,
            Long userId,
            MultipartFile file
    ) {
        requireMember(groupId, userId);

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Select a document to upload.");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("Maximum file size is 50 MB.");
        }

        String originalName = file.getOriginalFilename();

        if (originalName == null || originalName.isBlank()) {
            throw new IllegalArgumentException("Filename is required.");
        }

        // Remove any client-supplied directory components.
        originalName = Paths.get(
                originalName.replace('\\', '/')
        ).getFileName().toString().trim();

        if (originalName.isBlank() || originalName.length() > 255) {
            throw new IllegalArgumentException("Invalid filename.");
        }

        String format = validator.detectAndValidateFormat(file, originalName);

        Path groupDirectory = getGroupDirectory(groupId);
        Path target = null;

        try {
            Files.createDirectories(groupDirectory);

            String safeName = originalName.replaceAll("[^a-zA-Z0-9._-]", "_");
            target = groupDirectory.resolve(UUID.randomUUID() + "_" + safeName)
                    .normalize();

            if (!target.startsWith(groupDirectory)) {
                throw new IllegalArgumentException("Invalid storage path.");
            }

            Files.copy(file.getInputStream(), target);

            StudyGroupDocument document = new StudyGroupDocument();
            document.setStudyGroupId(groupId);
            document.setUploadedBy(userId);
            document.setOriginalFilename(originalName);
            document.setStoredFilePath(target.toString());
            document.setFileFormat(format);
            document.setFileSize(file.getSize());

            StudyGroupDocument saved = documentRepository.save(document);
            return toResponse(saved);

        } catch (IOException e) {
            deleteStoredFile(target);
            throw new RuntimeException("Could not store the uploaded document.", e);
        } catch (RuntimeException e) {
            deleteStoredFile(target);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> list(Long groupId, Long userId) {
        requireMember(groupId, userId);

        List<StudyGroupDocument> documents =
                documentRepository.findByStudyGroupIdOrderByCreatedAtDesc(groupId);

        List<DocumentResponse> result = new ArrayList<>();

        for (StudyGroupDocument document : documents) {
            result.add(toResponse(document));
        }

        return result;
    }

    @Transactional(readOnly = true)
    public DownloadFile download(
            Long groupId,
            Long documentId,
            Long userId
    ) {
        requireMember(groupId, userId);

        StudyGroupDocument document = documentRepository
                .findByIdAndStudyGroupId(documentId, groupId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Document not found."));

        Path groupDirectory = getGroupDirectory(groupId);
        Path storedPath = Paths.get(document.getStoredFilePath())
                .toAbsolutePath()
                .normalize();

        if (!storedPath.startsWith(groupDirectory)) {
            throw new IllegalArgumentException("Invalid document storage path.");
        }

        try {
            Resource resource = new UrlResource(storedPath.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                throw new IllegalArgumentException("Stored document is unavailable.");
            }

            return new DownloadFile(
                    resource,
                    document.getOriginalFilename(),
                    document.getFileFormat(),
                    document.getFileSize()
            );

        } catch (IOException e) {
            throw new RuntimeException("Could not read the requested document.", e);
        }
    }

    private void requireMember(Long groupId, Long userId) {
        if (userId == null || !userRepository.existsById(userId)) {
            throw new IllegalArgumentException("Authenticated user not found.");
        }

        if (!groupRepository.existsById(groupId)) {
            throw new IllegalArgumentException("Study group not found.");
        }

        if (!memberRepository.existsByStudyGroupIdAndUserId(groupId, userId)) {
            throw new IllegalArgumentException(
                    "Only study group members can access shared documents."
            );
        }
    }

    private Path getGroupDirectory(Long groupId) {
        return dataRoot.resolve("study_hall_documents")
                .resolve("group_" + groupId)
                .normalize();
    }

    private DocumentResponse toResponse(StudyGroupDocument document) {
        String uploaderName = userRepository.findById(document.getUploadedBy())
                .map(User::getName)
                .orElse("Unknown user");

        return new DocumentResponse(
                document.getId(),
                document.getStudyGroupId(),
                document.getUploadedBy(),
                uploaderName,
                document.getOriginalFilename(),
                document.getFileFormat(),
                document.getFileSize(),
                document.getCreatedAt() != null
                        ? document.getCreatedAt().toString()
                        : null
        );
    }

    private void deleteStoredFile(Path path) {
        if (path != null) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
                // Preserve the original upload/database error.
            }
        }
    }
}
