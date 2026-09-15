package com.astralib.backend;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/knowledge-graph")
public class KnowledgeGraphController {

    private final KnowledgeGraphService
            knowledgeGraphService;


    public KnowledgeGraphController(
            KnowledgeGraphService knowledgeGraphService
    ) {
        this.knowledgeGraphService =
                knowledgeGraphService;
    }


    // =========================================================
    // PHASE 1
    // BOOK METADATA GRAPH
    // =========================================================

    @GetMapping("/books/{bookId}")
    public ResponseEntity<?> getBookGraph(
            @PathVariable Long bookId
    ) {

        try {

            return ResponseEntity.ok(
                    knowledgeGraphService
                            .getBookGraph(
                                    bookId
                            )
            );

        } catch (IllegalArgumentException e) {

            return badRequest(
                    e.getMessage(),
                    bookId
            );
        }
    }


    // =========================================================
    // PHASE 2
    // CONCEPT GRAPH FOR ONE SECTION
    // =========================================================

    @GetMapping(
            "/books/{bookId}/concepts"
    )
    public ResponseEntity<?> getSectionConceptGraph(

            @PathVariable
            Long bookId,

            @RequestParam
            String section,

            @RequestParam(
                    defaultValue = "12"
            )
            int limit

    ) {

        try {

            return ResponseEntity.ok(
                    knowledgeGraphService
                            .getSectionConceptGraph(
                                    bookId,
                                    section,
                                    limit
                            )
            );

        } catch (IllegalArgumentException e) {

            return badRequest(
                    e.getMessage(),
                    bookId
            );
        }
    }


    // =========================================================
    // ERROR RESPONSE
    // =========================================================

    private ResponseEntity<Map<String, Object>>
    badRequest(
            String message,
            Long bookId
    ) {

        Map<String, Object> error =
                new LinkedHashMap<>();

        error.put(
                "message",
                message
        );

        error.put(
                "bookId",
                bookId
        );

        return ResponseEntity
                .badRequest()
                .body(error);
    }
}