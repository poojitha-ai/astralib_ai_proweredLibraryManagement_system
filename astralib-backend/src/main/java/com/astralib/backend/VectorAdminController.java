package com.astralib.backend;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class VectorAdminController {

    @Autowired
    private VectorIngestionService vectorIngestionService;

    @Autowired
    private VectorRecordRepository vectorRecordRepository;

    // =========================================================
    // 1. INGEST ALL VECTORS
    // =========================================================

    @PostMapping("/vector-ingest")
    public ResponseEntity<?> triggerVectorIngestion() {

        Map<String, Object> stats =
                vectorIngestionService.ingestAllVectors();

        return ResponseEntity.ok(stats);
    }

    // =========================================================
    // 2. RE-INGEST ALL VECTORS
    //
    // WARNING:
    // Deletes all existing vector records first.
    // =========================================================

    @PostMapping("/vector-reingest")
    public ResponseEntity<?> reingestVectors() {

        return ResponseEntity.ok(
                vectorIngestionService.reingestAllVectors()
        );
    }

    // =========================================================
    // 3. INGEST ONLY ONE BOOK
    //
    // Example:
    // POST /api/admin/vector-ingest/book/3003
    // =========================================================

    @PostMapping("/vector-ingest/book/{bookId}")
    public ResponseEntity<?> ingestSingleBook(
            @PathVariable Long bookId) {

        try {

            Map<String, Object> result =
                    vectorIngestionService
                            .ingestBookVectors(bookId);

            return ResponseEntity.ok(result);

        } catch (IllegalArgumentException e) {

            return ResponseEntity
                    .badRequest()
                    .body(
                            Map.of(
                                    "error",
                                    e.getMessage()
                            )
                    );

        } catch (IllegalStateException e) {

            return ResponseEntity
                    .badRequest()
                    .body(
                            Map.of(
                                    "error",
                                    "Book vector ingestion failed.",
                                    "detail",
                                    e.getMessage()
                            )
                    );

        } catch (Exception e) {

            return ResponseEntity
                    .status(500)
                    .body(
                            Map.of(
                                    "error",
                                    "Unexpected vector ingestion error.",
                                    "detail",
                                    e.getMessage()
                            )
                    );
        }
    }

    // =========================================================
    // 4. VECTOR STATS
    // =========================================================

    @GetMapping("/vector-stats")
    public ResponseEntity<?> getVectorStats() {

        Map<String, Object> stats =
                new LinkedHashMap<>();

        stats.put(
                "totalVectorRecords",
                vectorRecordRepository.count()
        );

        stats.put(
                "bookVectorsCount",
                vectorRecordRepository
                        .countByDocumentType("book")
        );
 
        stats.put(
                "paperVectorsCount",
                vectorRecordRepository
                        .countByDocumentType("paper")
        );

        stats.put(
                "documentVectorsCount",
                vectorRecordRepository
                        .countByDocumentType("document")
        );

        return ResponseEntity.ok(stats);
    }
}