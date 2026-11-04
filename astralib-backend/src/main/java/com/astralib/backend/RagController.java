
package com.astralib.backend;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/rag")
public class RagController {

    private static final String STUDENT_DOCUMENT_PREFIX = "STUDENT-DOC-";

    @Autowired
    private RagService ragService;

    @Autowired
    private VectorRecordRepository vectorRecordRepository;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private StudentDocumentRepository studentDocumentRepository;

    // =========================================================
    // GENERAL RAG
    // =========================================================

    @PostMapping("/ask")
    public ResponseEntity<RagService.RagResponseDTO> askRagPost(
            @RequestBody Map<String, Object> request) {

        String question = request.get("question") != null
                ? request.get("question").toString()
                : "";

        int topK = extractTopK(request);

        return ResponseEntity.ok(
                ragService.queryRag(question, topK)
        );
    }

    // =========================================================
    // DOCUMENT / BOOK SPECIFIC RAG
    // =========================================================

    @PostMapping("/documents/{documentId}/ask")
    public ResponseEntity<?> askDocumentRagPost(
            @PathVariable String documentId,
            @RequestBody Map<String, Object> request) {

        /*
         * Private student documents must use:
         * /api/student-documents/{id}/ask
         *
         * This prevents bypassing ownership validation.
         */
        if (isStudentDocumentId(documentId)) {
            return ResponseEntity.notFound().build();
        }

        String question = request.get("question") != null
                ? request.get("question").toString()
                : "";

        int topK = extractTopK(request);

        String resolvedDocumentId = resolveDocumentId(documentId);

        RagService.RagResponseDTO response =
                ragService.queryDocumentRag(
                        resolvedDocumentId,
                        question,
                        topK
                );

        return ResponseEntity.ok(response);
    }

    // =========================================================
    // COMPATIBILITY ENDPOINT
    // =========================================================

    @PostMapping("/query")
    public ResponseEntity<RagService.RagResponseDTO> queryRagPost(
            @RequestBody Map<String, Object> request) {

        return askRagPost(request);
    }

    // =========================================================
    // GET GENERAL RAG
    // =========================================================

    @GetMapping("/ask")
    public ResponseEntity<RagService.RagResponseDTO> queryRagGet(
            @RequestParam String question,
            @RequestParam(defaultValue = "3") int topK) {

        return ResponseEntity.ok(
                ragService.queryRag(
                        question,
                        Math.max(1, Math.min(topK, 20))
                )
        );
    }

    // =========================================================
    // RESOLVE BOOK ID -> VECTOR DOCUMENT ID
    // =========================================================

    private String resolveDocumentId(String incomingId) {

        if (incomingId == null || incomingId.isBlank()) {
            return incomingId;
        }

        String normalizedId = incomingId.trim();

        // Check whether the incoming ID is an AstraLib Book ID.
        try {
            Long bookId = Long.valueOf(normalizedId);

            Optional<Book> bookOptional =
                    bookRepository.findById(bookId);

            if (bookOptional.isPresent()) {
                Book book = bookOptional.get();

                if (book.getResourceId() != null
                        && !book.getResourceId().isBlank()) {

                    return book.getResourceId().trim();
                }

                return "BOOK-ID-" + book.getId();
            }

        } catch (NumberFormatException ignored) {
            // Non-numeric document IDs are used directly.
        }

        return normalizedId;
    }

    // =========================================================
    // TOP-K PARSER
    // =========================================================

    private int extractTopK(Map<String, Object> request) {

        int topK = 3;

        if (request != null && request.get("topK") != null) {
            try {
                topK = Integer.parseInt(
                        request.get("topK").toString()
                );
            } catch (Exception ignored) {
                topK = 3;
            }
        }

        return Math.max(1, Math.min(topK, 20));
    }

    // =========================================================
    // STUDENT DOCUMENT ID CHECK
    // =========================================================

    private boolean isStudentDocumentId(String documentId) {

        return documentId != null
                && documentId.trim()
                        .regionMatches(
                                true,
                                0,
                                STUDENT_DOCUMENT_PREFIX,
                                0,
                                STUDENT_DOCUMENT_PREFIX.length()
                        );
    }

    // =========================================================
    // CITATION CHUNK LOOKUP
    // =========================================================

    @GetMapping("/citations/{chunkId}")
    public ResponseEntity<?> getCitationChunk(
            Authentication authentication,
            @PathVariable String chunkId) {

        Optional<VectorRecord> recordOptional =
                vectorRecordRepository.findByChunkId(chunkId);

        if (recordOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        VectorRecord record = recordOptional.get();

        /*
         * Private student document citations require ownership
         * verification before returning any chunk content.
         */
        if ("student_document".equalsIgnoreCase(
                record.getDocumentType())) {

            Long userId = CurrentUser.id(authentication);

            if (userId == null) {
                return CurrentUser.unauthorizedResponse();
            }

            String documentId = record.getDocumentId();

            if (!isStudentDocumentId(documentId)) {
                return ResponseEntity.notFound().build();
            }

            String numericId = documentId.substring(
                    STUDENT_DOCUMENT_PREFIX.length()
            );

            try {
                Long studentDocumentId = Long.valueOf(numericId);

                boolean ownsDocument =
                        studentDocumentRepository
                                .findByIdAndUser_Id(
                                        studentDocumentId,
                                        userId
                                )
                                .isPresent();

                if (!ownsDocument) {
                    return ResponseEntity.notFound().build();
                }

            } catch (NumberFormatException e) {
                return ResponseEntity.notFound().build();
            }
        }

        // Return citation details only after access validation.
        Map<String, Object> response = new LinkedHashMap<>();

        response.put("chunkId", record.getChunkId());
        response.put("documentId", record.getDocumentId());
        response.put("documentType", record.getDocumentType());
        response.put("title", record.getTitle());
        response.put("author", record.getAuthor());
        response.put("section", record.getSection());
        response.put("chunkIndex", record.getChunkIndex());
        response.put("content", record.getContent());

        return ResponseEntity.ok(response);
    }
}
