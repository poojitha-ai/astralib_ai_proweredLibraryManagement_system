package com.astralib.backend;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/**
 * In-memory index of the stored embeddings.
 *
 * Smart Search used to parse every row's JSON embedding on every query: with
 * 73k passages that meant 30-35 seconds per search, which is not a usable
 * search box and made evaluation runs take hours. The parsed vectors are
 * therefore held once in memory and reused.
 *
 * Nothing about the vectors changes: they are the same values stored in
 * vector_records, only parsed ahead of time. The index is rebuilt when the
 * row count or highest id moves, so an ingestion run is picked up
 * automatically without a restart and without a migration.
 */
@Service
public class VectorIndexService {

    private static final Logger logger =
            LoggerFactory.getLogger(VectorIndexService.class);

    @Autowired
    private VectorRecordRepository vectorRecordRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile Snapshot snapshot;

    /**
     * The table is checked for changes at most this often. Without it every
     * one of the 73k per-passage lookups during a single search would issue
     * its own count query.
     */
    private static final long RECHECK_INTERVAL_MS = 5_000L;

    private volatile long lastCheckMillis;

    /**
     * Normalised searchable text per chunk, filled on demand. Lexical scoring
     * used to normalise the full content of all 73k rows on every query,
     * which is what made hybrid search take about nine seconds.
     */
    private final Map<String, String> normalizedText = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * One immutable view of the vector table.
     */
    private static final class Snapshot {

        private final long fingerprint;
        private final List<VectorRecord> records;
        private final Map<String, float[]> vectors;
        private final Map<String, List<VectorRecord>> byDocumentType;

        private Snapshot(
                long fingerprint,
                List<VectorRecord> records,
                Map<String, float[]> vectors,
                Map<String, List<VectorRecord>> byDocumentType) {

            this.fingerprint = fingerprint;
            this.records = records;
            this.vectors = vectors;
            this.byDocumentType = byDocumentType;
        }
    }

    /**
     * All stored passages, at most one build per change to the table.
     */
    public List<VectorRecord> records() {
        return snapshot().records;
    }

    /**
     * Passages of one document type ("book", "paper"), or all of them.
     */
    public List<VectorRecord> records(String documentType) {

        if (
                documentType == null
                        || documentType.isBlank()
                        || "all".equalsIgnoreCase(documentType)
        ) {
            return records();
        }

        List<VectorRecord> filtered =
                snapshot().byDocumentType.get(
                        documentType.toLowerCase(Locale.ROOT)
                );

        return filtered == null
                ? Collections.emptyList()
                : filtered;
    }

    /**
     * The parsed embedding for a chunk. Falls back to parsing the stored JSON
     * if a row arrived after the index was built.
     */
    public float[] vectorFor(VectorRecord record) {

        float[] cached =
                snapshot().vectors.get(record.getChunkId());

        if (cached != null) {
            return cached;
        }

        try {
            return objectMapper.readValue(
                    record.getEmbeddingJson(),
                    float[].class
            );
        } catch (Exception e) {
            logger.warn(
                    "Could not parse embedding for chunk {}: {}",
                    record.getChunkId(),
                    e.getMessage()
            );
            return null;
        }
    }

    public int size() {
        return snapshot().records.size();
    }

    /**
     * Normalised title, author, category, section and content for one chunk,
     * in that order. Cached because lexical scoring reads all five per
     * candidate and re-normalising them made hybrid search slow.
     */
    public String[] normalizedFields(VectorRecord record) {

        return normalizedFieldCache.computeIfAbsent(
                record.getChunkId(),
                key -> new String[]{ 
                        normalize(record.getTitle()),
                        normalize(record.getAuthor()),
                        normalize(record.getCategory()),
                        normalize(record.getSection()),
                        normalize(record.getContent())
                }
        );
    }

    private final Map<String, String[]> normalizedFieldCache =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Lower-case, punctuation-free text used by lexical scoring.
     */
    public String normalizedText(VectorRecord record) {

        return normalizedText.computeIfAbsent(
                record.getChunkId(),
                key -> normalize(record.getContent())
        );
    }

    public String normalizedField(String value) {
        return normalize(value);
    }

    private String normalize(String value) {

        if (value == null) {
            return "";
        }

        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }

    /**
     * Build the index while nobody is waiting for it. The first search would
     * otherwise pay the whole parse cost (about 35 s on the full table).
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {

        Thread builder = new Thread(() -> {
            try {
                snapshot();
            } catch (Exception e) {
                logger.warn("Vector index warm-up failed: {}", e.getMessage());
            }
        }, "vector-index-warmup");

        builder.setDaemon(true);
        builder.start();
    }

    // =========================================================
    // BUILD
    // =========================================================

    private Snapshot snapshot() {

        Snapshot current = snapshot;

        long now = System.currentTimeMillis();

        if (
                current != null
                        && now - lastCheckMillis < RECHECK_INTERVAL_MS
        ) {
            return current;
        }

        synchronized (this) {

            current = snapshot;
            now = System.currentTimeMillis();

            if (
                    current != null
                            && now - lastCheckMillis < RECHECK_INTERVAL_MS
            ) {
                return current;
            }

            long fingerprint =
                    vectorRecordRepository.count() * 1_000_003L
                            + maxId();

            lastCheckMillis = now;

            if (current != null && current.fingerprint == fingerprint) {
                return current;
            }

            long started = System.currentTimeMillis();

            List<VectorRecord> records = new ArrayList<>();
            for (VectorRecord r : vectorRecordRepository.findAll()) {
                if (!"student_document".equalsIgnoreCase(r.getDocumentType())) {
                    records.add(r);
                }
            }

            Map<String, float[]> vectors =
                    new HashMap<>(records.size() * 2);

            for (VectorRecord record : records) {

                if (record.getEmbeddingJson() == null) {
                    continue;
                }

                try {
                    vectors.put(
                            record.getChunkId(),
                            objectMapper.readValue(
                                    record.getEmbeddingJson(),
                                    float[].class
                            )
                    );
                } catch (Exception e) {
                    logger.warn(
                            "Skipping unreadable embedding for chunk {}: {}",
                            record.getChunkId(),
                            e.getMessage()
                    );
                }
            }

            Map<String, List<VectorRecord>> byType = new HashMap<>();

            for (VectorRecord record : records) {

                String type = record.getDocumentType() == null
                        ? ""
                        : record.getDocumentType().toLowerCase(Locale.ROOT);

                byType.computeIfAbsent(type, key -> new ArrayList<>())
                        .add(record);
            }

            Snapshot built =
                    new Snapshot(fingerprint, records, vectors, byType);

            snapshot = built;

            logger.info(
                    "Vector index ready: {} passages, {} embeddings parsed in {} ms",
                    records.size(),
                    vectors.size(),
                    System.currentTimeMillis() - started
            );

            return built;
        }
    }

    private long maxId() {
        Long max = vectorRecordRepository.findMaxId();
        return max == null ? 0L : max;
    }
}
