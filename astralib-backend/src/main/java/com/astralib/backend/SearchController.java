package com.astralib.backend;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    @Autowired
    private SemanticSearchService semanticSearchService;

    @Autowired
    private SmartSearchService smartSearchService;

    @Autowired
    private QueryUnderstandingService queryUnderstandingService;

    /**
     * Natural-language search: catalogue recommendations and cited passages,
     * kept separate.
     */
    @GetMapping("/smart")
    public ResponseEntity<?> smartSearch(
            @RequestParam String query,
            @RequestParam(defaultValue = "6") int topK,
            @RequestParam(required = false) String documentType) {
        try {
            return ResponseEntity.ok(
                    smartSearchService.search(query, topK, documentType)
            );
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Search is not available: no embedding provider configured.",
                    "detail", String.valueOf(e.getMessage())
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "error", "Search failed: " + e.getMessage()
            ));
        }
    }

    /**
     * Semantic retrieval for natural-language requests.
     *
     * The response is an envelope rather than a bare list: a request like
     * "new book to read and easy to understand" is a catalogue question and
     * has no passage to return, so the answer has to be able to carry book
     * recommendations and a sentence explaining the outcome alongside any
     * cited passages.
     */
    @GetMapping("/semantic")
    public ResponseEntity<?> semanticSearch(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int topK,
            @RequestParam(required = false) String documentType) {
        try {
            return ResponseEntity.ok(
                smartSearchService.search(query, topK, documentType, "semantic")
            );
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Semantic search is not available: no embedding provider configured. Use Keyword Search instead.",
                "detail", String.valueOf(e.getMessage())
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "error", "Search failed: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/hybrid")
    public ResponseEntity<?> hybridSearch(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int topK,
            @RequestParam(required = false) String documentType) {
        try {
            /*
             * Hybrid reads a natural-language request, so it gets the same
             * vocabulary expansion as the semantic path before matching. The
             * literal query is kept as the keyword signal: "pointers" must
             * still match the word, while the expansion helps the embedding.
             */
            String expanded = queryUnderstandingService
                    .plan(query)
                    .retrievalQuery();

            List<SemanticSearchService.SearchResultDTO> results =
                semanticSearchService.performHybridSearch(expanded, topK, documentType);
            return ResponseEntity.ok(results);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Hybrid search is not available: no embedding provider configured. Use Keyword Search instead.",
                "detail", String.valueOf(e.getMessage())
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "error", "Search failed: " + e.getMessage()
            ));
        }
    }

    /**
     * Lexical retrieval on its own, kept as an independent engine so a query
     * that defeats the embedding model still has a working path.
     */
    @GetMapping("/keyword")
    public ResponseEntity<?> keywordSearch(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int topK,
            @RequestParam(required = false) String documentType) {
        try {
            return ResponseEntity.ok(
                    semanticSearchService.performKeywordSearch(query, topK, documentType)
            );
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "error", "Keyword search failed: " + e.getMessage()
            ));
        }
    }
}
