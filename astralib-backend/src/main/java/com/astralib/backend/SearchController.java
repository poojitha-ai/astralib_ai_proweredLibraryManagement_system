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

    @GetMapping("/semantic")
    public ResponseEntity<?> semanticSearch(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int topK,
            @RequestParam(required = false) String documentType) {
        try {
            List<SemanticSearchService.SearchResultDTO> results =
                semanticSearchService.performSemanticSearch(query, topK, documentType);
            return ResponseEntity.ok(results);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Semantic search is not available: no embedding provider configured. Use Keyword Search instead.",
                "detail", e.getMessage()
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
            List<SemanticSearchService.SearchResultDTO> results =
                semanticSearchService.performHybridSearch(query, topK, documentType);
            return ResponseEntity.ok(results);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Hybrid search is not available: no embedding provider configured. Use Keyword Search instead.",
                "detail", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "error", "Search failed: " + e.getMessage()
            ));
        }
    }
}
