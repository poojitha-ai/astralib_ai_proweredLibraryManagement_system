
package com.astralib.backend;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * REST controller for private student document management and Q&A.
 *
 * All operations derive ownership from JWT authentication.
 * Cross-user document lookups resolve as 404.
 */
@RestController
@RequestMapping("/api/student-documents")
public class StudentDocumentController {

    @Autowired
    private StudentDocumentService studentDocumentService;

    @Autowired
    private RagService ragService;

    public record AskRequest(
            String question,
            Integer topK
    ) {}

    @PostMapping(consumes = {"multipart/form-data"})
    public ResponseEntity<?> upload(
            Authentication authentication,
            @RequestParam("file") MultipartFile file) {

        Long userId = CurrentUser.id(authentication);
        if (userId == null) {
            return CurrentUser.unauthorizedResponse();
        }

        try {
            StudentDocumentService.DocumentResponse response =
                    studentDocumentService.uploadDocument(userId, file);

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (StudentDocumentService.DocumentValidationException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Upload processing failed: " + e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<?> list(Authentication authentication) {

        Long userId = CurrentUser.id(authentication);
        if (userId == null) {
            return CurrentUser.unauthorizedResponse();
        }

        return ResponseEntity.ok(studentDocumentService.listDocuments(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(
            Authentication authentication,
            @PathVariable Long id) {

        Long userId = CurrentUser.id(authentication);
        if (userId == null) {
            return CurrentUser.unauthorizedResponse();
        }

        try {
            return ResponseEntity.ok(
                    studentDocumentService.getDocument(userId, id)
            );

        } catch (StudentDocumentService.DocumentNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Document not found: " + id));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(
            Authentication authentication,
            @PathVariable Long id) {

        Long userId = CurrentUser.id(authentication);
        if (userId == null) {
            return CurrentUser.unauthorizedResponse();
        }

        try {
            studentDocumentService.deleteDocument(userId, id);

            return ResponseEntity.ok(
                    Map.of("deleted", true, "id", id)
            );

        } catch (StudentDocumentService.DocumentNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Document not found: " + id));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Deletion failed: " + e.getMessage()));
        }
    }

    /**
     * Ask questions about a private student document.
     *
     * POST /api/student-documents/{id}/ask
     *
     * Ownership is verified before retrieving document chunks.
     */
    @PostMapping("/{id}/ask")
    public ResponseEntity<?> askDocument(
            Authentication authentication,
            @PathVariable Long id,
            @RequestBody AskRequest request) {

        Long userId = CurrentUser.id(authentication);

        if (userId == null) {
            return CurrentUser.unauthorizedResponse();
        }

        if (request == null ||
                request.question() == null ||
                request.question().isBlank()) {

            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Question cannot be empty."));
        }

        try {
            // This lookup verifies that the document belongs to this user.
            StudentDocumentService.DocumentResponse document =
                    studentDocumentService.getDocument(userId, id);

            // Do not query documents that have not finished processing.
            if (!"READY".equalsIgnoreCase(document.status())) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of(
                                "error", "Document is not ready for questions.",
                                "status", document.status()
                        ));
            }

            int topK = request.topK() == null ? 5 : request.topK();

            if (topK < 1 || topK > 20) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "topK must be between 1 and 20."));
            }

            String documentId = "STUDENT-DOC-" + id;

            RagService.RagResponseDTO response = ragService.queryDocumentRag(
                    documentId,
                    request.question().trim(),
                    topK
            );

            return ResponseEntity.ok(response);

        } catch (StudentDocumentService.DocumentNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Document not found: " + id));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Document question failed."));
        }
    }
}
