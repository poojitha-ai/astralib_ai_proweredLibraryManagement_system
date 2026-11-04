package com.astralib.backend;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/books")
public class BookImportController {

    @Autowired
    private BookImportService bookImportService;

    @Autowired
    private BookIngestionJobService bookIngestionJobService;


    // =========================================================
    // EMBEDDING STATUS FOR ONE IMPORT JOB
    // =========================================================

    @GetMapping("/import-status/{jobId}")
    public ResponseEntity<?> getImportStatus(
            @PathVariable String jobId) {

        Map<String, Object> job =
                bookIngestionJobService.status(
                        jobId
                );

        if (job == null) {

            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(
                            Map.of(
                                    "success",
                                    false,
                                    "error",
                                    "Unknown ingestion job: " + jobId
                            )
                    );
        }

        return ResponseEntity.ok(job);
    }


    // =========================================================
    // LATEST EMBEDDING STATUS FOR A BOOK
    // =========================================================

    @GetMapping("/ingestion-status/{bookId}")
    public ResponseEntity<?> getBookIngestionStatus(
            @PathVariable Long bookId) {

        Map<String, Object> job =
                bookIngestionJobService.latestForBook(
                        bookId
                );

        if (job == null) {

            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(
                            Map.of(
                                    "success",
                                    false,
                                    "error",
                                    "No ingestion job recorded for book "
                                            + bookId
                            )
                    );
        }

        return ResponseEntity.ok(job);
    }


    @PostMapping(
            value = "/import-epub",
            consumes = "multipart/form-data"
    )
    public ResponseEntity<?> importEpub(
            @RequestParam("file")
            MultipartFile file,

            @RequestParam("resourceId")
            String resourceId,

            @RequestParam("title")
            String title,

            @RequestParam("author")
            String author,

            @RequestParam(
                    value = "category",
                    required = false
            )
            String category,

            @RequestParam(
                    value = "description",
                    required = false
            )
            String description,

            @RequestParam(
                    value = "publicationYear",
                    required = false
            )
            Integer publicationYear,

            @RequestParam(
                    value = "publisher",
                    required = false
            )
            String publisher,

            @RequestParam(
                    value = "language",
                    required = false
            )
            String language,

            @RequestParam(
                    value = "isbn",
                    required = false
            )
            String isbn,

            @RequestParam(
                    value = "coverImage",
                    required = false
            )
            String coverImage,

            @RequestParam(
                    value = "subjects",
                    required = false
            )
            String subjects
    ) {

        try {

            Map<String, Object> result =
                    bookImportService.importEpub(
                            file,
                            resourceId,
                            title,
                            author,
                            category,
                            description,
                            publicationYear,
                            publisher,
                            language,
                            isbn,
                            coverImage,
                            subjects
                    );


            return ResponseEntity.ok(
                    result
            );


        } catch (IllegalArgumentException e) {

            return ResponseEntity
                    .badRequest()
                    .body(
                            Map.of(
                                    "success",
                                    false,
                                    "error",
                                    e.getMessage()
                            )
                    );


        } catch (Exception e) {

            return ResponseEntity
                    .internalServerError()
                    .body(
                            Map.of(
                                    "success",
                                    false,
                                    "error",
                                    e.getMessage()
                            )
                    );
        }
    }
}