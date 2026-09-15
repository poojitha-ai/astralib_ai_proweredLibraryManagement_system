package com.astralib.backend;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/rag")
public class RagController {

    @Autowired
    private RagService ragService;

    @Autowired
    private VectorRecordRepository vectorRecordRepository;

    @Autowired
    private BookRepository bookRepository;


    // =========================================================
    // GENERAL RAG
    // =========================================================

    @PostMapping("/ask")
    public ResponseEntity<RagService.RagResponseDTO> askRagPost(
            @RequestBody Map<String, Object> request
    ) {

        String question =
                request.get("question") != null
                        ? request.get("question").toString()
                        : "";

        int topK =
                extractTopK(
                        request
                );

        RagService.RagResponseDTO response =
                ragService.queryRag(
                        question,
                        topK
                );

        return ResponseEntity.ok(
                response
        );
    }


    // =========================================================
    // DOCUMENT / BOOK SPECIFIC RAG
    // =========================================================
    //
    // This endpoint accepts BOTH:
    //
    // /api/rag/documents/3003/ask
    //
    // and
    //
    // /api/rag/documents/GUTENBERG-1661/ask
    //
    // If a normal AstraLib Book ID is supplied,
    // we resolve it to the actual document ID used by
    // vector_records before calling the RAG service.
    //
    // =========================================================

    @PostMapping("/documents/{documentId}/ask")
    public ResponseEntity<RagService.RagResponseDTO>
    askDocumentRagPost(
            @PathVariable String documentId,
            @RequestBody Map<String, Object> request
    ) {

        String question =
                request.get("question") != null
                        ? request.get("question").toString()
                        : "";

        int topK =
                extractTopK(
                        request
                );

        String resolvedDocumentId =
                resolveDocumentId(
                        documentId
                );

        System.out.println(
                "Document RAG request: "
                        + documentId
                        + " -> "
                        + resolvedDocumentId
        );

        RagService.RagResponseDTO response =
                ragService.queryDocumentRag(
                        resolvedDocumentId,
                        question,
                        topK
                );

        return ResponseEntity.ok(
                response
        );
    }


    // =========================================================
    // COMPATIBILITY ENDPOINT
    // =========================================================

    @PostMapping("/query")
    public ResponseEntity<RagService.RagResponseDTO>
    queryRagPost(
            @RequestBody Map<String, Object> request
    ) {

        return askRagPost(
                request
        );
    }


    // =========================================================
    // GET GENERAL RAG
    // =========================================================

    @GetMapping("/ask")
    public ResponseEntity<RagService.RagResponseDTO>
    queryRagGet(
            @RequestParam String question,
            @RequestParam(defaultValue = "3") int topK
    ) {

        RagService.RagResponseDTO response =
                ragService.queryRag(
                        question,
                        topK
                );

        return ResponseEntity.ok(
                response
        );
    }


    // =========================================================
    // RESOLVE BOOK ID -> VECTOR DOCUMENT ID
    // =========================================================
    //
    // Example:
    //
    // Incoming:
    //     3003
    //
    // Book table:
    //     id = 3003
    //     resourceId = GUTENBERG-1661
    //
    // Result:
    //     GUTENBERG-1661
    //
    // Non-numeric IDs such as GUTENBERG-1661 are returned
    // unchanged.
    //
    // =========================================================

    private String resolveDocumentId(
            String incomingId
    ) {

        if (
                incomingId == null
                        ||
                        incomingId.isBlank()
        ) {

            return incomingId;
        }

        String normalizedId =
                incomingId.trim();


        // -----------------------------------------------------
        // First:
        // Check whether frontend supplied a normal Book ID.
        // -----------------------------------------------------

        try {

            Long bookId =
                    Long.valueOf(
                            normalizedId
                    );

            Optional<Book> bookOptional =
                    bookRepository.findById(
                            bookId
                    );

            if (
                    bookOptional.isPresent()
            ) {

                Book book =
                        bookOptional.get();

                if (
                        book.getResourceId() != null
                                &&
                                !book.getResourceId()
                                        .isBlank()
                ) {

                    return book
                            .getResourceId()
                            .trim();
                }


                // Same fallback format used for books
                // without an external resource ID.
                return "BOOK-ID-"
                        + book.getId();
            }

        } catch (
                NumberFormatException ignored
        ) {

            // Not a numeric Book ID.
            // Continue using the supplied document ID.
        }


        // -----------------------------------------------------
        // Already a proper document ID.
        //
        // Example:
        // GUTENBERG-1661
        // -----------------------------------------------------

        return normalizedId;
    }


    // =========================================================
    // TOP-K PARSER
    // =========================================================

    private int extractTopK(
            Map<String, Object> request
    ) {

        int topK = 3;

        if (
                request != null
                        &&
                        request.get("topK") != null
        ) {

            try {

                topK =
                        Integer.parseInt(
                                request
                                        .get("topK")
                                        .toString()
                        );

            } catch (
                    Exception ignored
            ) {

                topK = 3;
            }
        }


        // Keep RAG requests inside a safe range.
        return Math.max(
                1,
                Math.min(
                        topK,
                        20
                )
        );
    }


    // =========================================================
    // CITATION CHUNK LOOKUP
    // =========================================================

    @GetMapping("/citations/{chunkId}")
    public ResponseEntity<?> getCitationChunk(
            @PathVariable String chunkId
    ) {

        Optional<VectorRecord> recordOptional =
                vectorRecordRepository
                        .findByChunkId(
                                chunkId
                        );

        if (
                recordOptional.isEmpty()
        ) {

            Map<String, Object> error =
                    new LinkedHashMap<>();

            error.put(
                    "message",
                    "Citation chunk not found"
            );

            error.put(
                    "chunkId",
                    chunkId
            );

            return ResponseEntity
                    .notFound()
                    .build();
        }


        VectorRecord record =
                recordOptional.get();


        Map<String, Object> response =
                new LinkedHashMap<>();

        response.put(
                "chunkId",
                record.getChunkId()
        );

        response.put(
                "documentId",
                record.getDocumentId()
        );

        response.put(
                "documentType",
                record.getDocumentType()
        );

        response.put(
                "title",
                record.getTitle()
        );

        response.put(
                "author",
                record.getAuthor()
        );

        response.put(
                "section",
                record.getSection()
        );

        response.put(
                "chunkIndex",
                record.getChunkIndex()
        );

        response.put(
                "content",
                record.getContent()
        );


        return ResponseEntity.ok(
                response
        );
    }
}