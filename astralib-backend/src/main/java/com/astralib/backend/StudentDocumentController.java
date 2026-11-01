package com.astralib.backend;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * REST controller for private student document management.
 *
 * All operations derive user ownership from JWT authentication.
 * No client-supplied user ID is accepted, and cross-user lookups resolve as 404.
 */
@RestController
@RequestMapping("/api/student-documents")
public class StudentDocumentController {

    @Autowired
    private StudentDocumentService studentDocumentService;

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
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
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
            return ResponseEntity.ok(studentDocumentService.getDocument(userId, id));
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
            return ResponseEntity.ok(Map.of("deleted", true, "id", id));
        } catch (StudentDocumentService.DocumentNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Document not found: " + id));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Deletion failed: " + e.getMessage()));
        }
    }
}
