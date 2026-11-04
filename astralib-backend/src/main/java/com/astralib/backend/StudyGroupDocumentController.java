
package com.astralib.backend;

import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/study-groups/{groupId}/documents")
public class StudyGroupDocumentController {

    private final StudyGroupDocumentService documentService;
    private final StudyHallWebSocketHandler webSocketHandler;

    public StudyGroupDocumentController(
            StudyGroupDocumentService documentService,
            StudyHallWebSocketHandler webSocketHandler
    ) {
        this.documentService = documentService;
        this.webSocketHandler = webSocketHandler;
    }

    @GetMapping
    public ResponseEntity<?> list(
            @PathVariable Long groupId,
            Authentication authentication
    ) {
        try {
            Long userId = getUserId(authentication);
            return ResponseEntity.ok(
                    documentService.list(groupId, userId)
            );
        } catch (IllegalArgumentException e) {
            return badRequest(e);
        }
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> upload(
            @PathVariable Long groupId,
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        try {
            Long userId = getUserId(authentication);

            StudyGroupDocumentService.DocumentResponse uploaded =
                    documentService.upload(groupId, userId, file);

            Map<String, Object> document = new LinkedHashMap<>();
            document.put("id", uploaded.id());
            document.put("studyGroupId", uploaded.studyGroupId());
            document.put("uploadedBy", uploaded.uploadedBy());
            document.put("uploaderName", uploaded.uploaderName());
            document.put("originalFilename", uploaded.originalFilename());
            document.put("fileFormat", uploaded.fileFormat());
            document.put("fileSize", uploaded.fileSize());
            document.put("createdAt", uploaded.createdAt());

            webSocketHandler.broadcastDocumentUploaded(
                    groupId,
                    document
            );

            return ResponseEntity.ok(uploaded);

        } catch (IllegalArgumentException e) {
            return badRequest(e);
        }
    }

    @GetMapping("/{documentId}/download")
    public ResponseEntity<?> download(
            @PathVariable Long groupId,
            @PathVariable Long documentId,
            Authentication authentication
    ) {
        try {
            Long userId = getUserId(authentication);

            StudyGroupDocumentService.DownloadFile file =
                    documentService.download(groupId, documentId, userId);

            ContentDisposition disposition =
                    ContentDisposition.attachment()
                            .filename(file.filename(), StandardCharsets.UTF_8)
                            .build();

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(file.fileSize())
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            disposition.toString()
                    )
                    .body(file.resource());

        } catch (IllegalArgumentException e) {
            return badRequest(e);
        }
    }

    private Long getUserId(Authentication authentication) {
        if (authentication == null ||
                authentication.getPrincipal() == null) {
            throw new IllegalArgumentException(
                    "Authentication required."
            );
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof Long) {
            return (Long) principal;
        }

        try {
            return Long.valueOf(principal.toString());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid authenticated user."
            );
        }
    }

    private ResponseEntity<?> badRequest(IllegalArgumentException e) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("error", e.getMessage());
        return ResponseEntity.badRequest().body(response);
    }
}
