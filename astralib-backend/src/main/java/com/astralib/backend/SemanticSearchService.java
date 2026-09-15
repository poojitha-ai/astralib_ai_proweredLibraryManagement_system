package com.astralib.backend;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SemanticSearchService {

    private static final Logger logger =
            LoggerFactory.getLogger(SemanticSearchService.class);

    /*
     * Starting threshold for semantic relevance.
     *
     * We can tune this later based on real AstraLib search tests.
     */
    private static final double MIN_SEMANTIC_SCORE = 0.60;

    /*
     * RAG can use a slightly lower threshold because it needs
     * several relevant passages from the same resource.
     *
     * Smart Search still keeps the stricter 0.60 threshold.
     */
    private static final double MIN_RAG_SCORE = 0.55;

    @Autowired
    private VectorRecordRepository vectorRecordRepository;

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private BookRepository bookRepository;

    private final ObjectMapper objectMapper =
            new ObjectMapper();

    // =========================================================
    // SEMANTIC SEARCH
    // =========================================================

    public List<SearchResultDTO> performSemanticSearch(
            String query,
            int topK,
            String documentType) {

        if (
                query == null ||
                query.isBlank()
        ) {
            return Collections.emptyList();
        }

        topK =
                Math.max(
                        1,
                        Math.min(topK, 100)
                );

        // -----------------------------------------------------
        // Convert query into embedding
        // -----------------------------------------------------

        float[] queryVector =
                embeddingService
                        .generateEmbedding(query);

        // -----------------------------------------------------
        // Load candidate vectors
        // -----------------------------------------------------

        List<VectorRecord> records;

        if (
                documentType != null &&
                !documentType.isBlank() &&
                !"all".equalsIgnoreCase(documentType)
        ) {

            records =
                    vectorRecordRepository
                            .findByDocumentType(
                                    documentType.toLowerCase()
                            );

        } else {

            records =
                    vectorRecordRepository.findAll();
        }

        Map<String, Long> bookIdMap =
                buildBookResourceIdMap();

        /*
         * IMPORTANT:
         *
         * key   = one unique resource
         * value = best matching chunk for that resource
         */
        Map<String, SearchResultDTO> bestResultPerResource =
                new LinkedHashMap<>();

        // -----------------------------------------------------
        // Compare all chunks
        // -----------------------------------------------------

        for (VectorRecord record : records) {

            try {

                float[] recordVector =
                        objectMapper.readValue(
                                record.getEmbeddingJson(),
                                float[].class
                        );

                double similarity =
                        computeCosineSimilarity(
                                queryVector,
                                recordVector
                        );

                /*
                 * Reject weak matches.
                 */
                if (
                        similarity <
                        MIN_SEMANTIC_SCORE
                ) {
                    continue;
                }

                double roundedScore =
                        Math.round(
                                similarity * 1000.0
                        ) / 1000.0;

                Long bookId =
                        resolveBookId(
                                record,
                                bookIdMap
                        );

                SearchResultDTO candidate =
                        new SearchResultDTO(
                                record.getChunkId(),
                                record.getDocumentId(),
                                bookId,
                                record.getDocumentType(),
                                record.getTitle(),
                                record.getAuthor(),
                                record.getCategory(),
                                record.getSection(),
                                record.getChunkIndex() != null
                                        ? record.getChunkIndex()
                                        : 0,
                                record.getContent(),
                                roundedScore
                        );

                String resourceKey =
                        buildResourceKey(
                                candidate
                        );

                SearchResultDTO existing =
                        bestResultPerResource
                                .get(resourceKey);

                /*
                 * Keep only the best chunk
                 * for each book/document.
                 */
                if (
                        existing == null ||
                        candidate.getSimilarityScore()
                                >
                        existing.getSimilarityScore()
                ) {

                    bestResultPerResource.put(
                            resourceKey,
                            candidate
                    );
                }

            } catch (Exception e) {

                logger.warn(
                        "Error parsing vector for chunk {}: {}",
                        record.getChunkId(),
                        e.getMessage()
                );
            }
        }

        return bestResultPerResource
                .values()
                .stream()
                .sorted(
                        (r1, r2) ->
                                Double.compare(
                                        r2.getSimilarityScore(),
                                        r1.getSimilarityScore()
                                )
                )
                .limit(topK)
                .collect(Collectors.toList());
    }

    // =========================================================
    // GLOBAL RAG CHUNK SEARCH
    //
    // IMPORTANT:
    // Unlike Smart Search, this method DOES NOT deduplicate
    // results by book/document.
    //
    // Research Assistant needs multiple relevant chunks from
    // the same source so the LLM receives enough context.
    // =========================================================

    public List<SearchResultDTO> performRagChunkSearch(
            String query,
            int topK,
            String documentType) {

        if (
                query == null ||
                query.isBlank()
        ) {
            return Collections.emptyList();
        }

        topK =
                Math.max(
                        1,
                        Math.min(topK, 100)
                );

        // -----------------------------------------------------
        // Convert query into embedding
        // -----------------------------------------------------

        float[] queryVector =
                embeddingService
                        .generateEmbedding(query);

        // -----------------------------------------------------
        // Load candidate vectors
        // -----------------------------------------------------

        List<VectorRecord> records;

        if (
                documentType != null &&
                !documentType.isBlank() &&
                !"all".equalsIgnoreCase(documentType)
        ) {

            records =
                    vectorRecordRepository
                            .findByDocumentType(
                                    documentType.toLowerCase()
                            );

        } else {

            records =
                    vectorRecordRepository.findAll();
        }

        Map<String, Long> bookIdMap =
                buildBookResourceIdMap();

        List<SearchResultDTO> results =
                new ArrayList<>();

        // -----------------------------------------------------
        // Compare every chunk independently
        // -----------------------------------------------------

        for (VectorRecord record : records) {

            try {

                float[] recordVector =
                        objectMapper.readValue(
                                record.getEmbeddingJson(),
                                float[].class
                        );

                double similarity =
                        computeCosineSimilarity(
                                queryVector,
                                recordVector
                        );

                /*
                 * RAG uses a slightly lower threshold than
                 * Smart Search so nearby supporting passages
                 * can still be included.
                 */
                if (
                        similarity <
                        MIN_RAG_SCORE
                ) {
                    continue;
                }

                double roundedScore =
                        Math.round(
                                similarity * 1000.0
                        ) / 1000.0;

                Long bookId =
                        resolveBookId(
                                record,
                                bookIdMap
                        );

                results.add(
                        new SearchResultDTO(
                                record.getChunkId(),
                                record.getDocumentId(),
                                bookId,
                                record.getDocumentType(),
                                record.getTitle(),
                                record.getAuthor(),
                                record.getCategory(),
                                record.getSection(),
                                record.getChunkIndex() != null
                                        ? record.getChunkIndex()
                                        : 0,
                                record.getContent(),
                                roundedScore
                        )
                );

            } catch (Exception e) {

                logger.warn(
                        "Error parsing vector for RAG chunk {}: {}",
                        record.getChunkId(),
                        e.getMessage()
                );
            }
        }

        /*
         * IMPORTANT:
         * No resource-level deduplication here.
         *
         * Example:
         * Sherlock chunk 95
         * Sherlock chunk 94
         * Sherlock chunk 96
         *
         * can all be returned if they are the top matches.
         */
        return results
                .stream()
                .sorted(
                        (r1, r2) ->
                                Double.compare(
                                        r2.getSimilarityScore(),
                                        r1.getSimilarityScore()
                                )
                )
                .limit(topK)
                .collect(Collectors.toList());
    }


    // =========================================================
    // DOCUMENT-SPECIFIC SEMANTIC SEARCH
    // Used later by Ask This Book / RAG
    //
    // IMPORTANT:
    // Do NOT deduplicate here.
    //
    // RAG needs multiple chunks from the same document.
    // =========================================================

    public List<SearchResultDTO> performDocumentSemanticSearch(
            String documentId,
            String query,
            int topK) {

        if (
                query == null ||
                query.isBlank() ||
                documentId == null ||
                documentId.isBlank()
        ) {
            return Collections.emptyList();
        }

        topK =
                Math.max(
                        1,
                        Math.min(topK, 100)
                );

        float[] queryVector =
                embeddingService
                        .generateEmbedding(query);

        List<VectorRecord> records =
                vectorRecordRepository
                        .findByDocumentId(documentId);

        if (records.isEmpty()) {

            records =
                    vectorRecordRepository
                            .findByDocumentIdContainingIgnoreCase(
                                    documentId
                            );
        }

        /*
         * Existing compatibility:
         * allow VectorRecord numeric ID.
         */
        if (records.isEmpty()) {

            try {

                Long id =
                        Long.parseLong(
                                documentId
                        );

                Optional<VectorRecord> record =
                        vectorRecordRepository
                                .findById(id);

                if (record.isPresent()) {

                    records =
                            vectorRecordRepository
                                    .findByDocumentId(
                                            record.get()
                                                    .getDocumentId()
                                    );
                }

            } catch (Exception ignored) {
                // documentId was not numeric
            }
        }

        Map<String, Long> bookIdMap =
                buildBookResourceIdMap();

        List<SearchResultDTO> results =
                new ArrayList<>();

        for (VectorRecord record : records) {

            try {

                float[] recordVector =
                        objectMapper.readValue(
                                record.getEmbeddingJson(),
                                float[].class
                        );

                double similarity =
                        computeCosineSimilarity(
                                queryVector,
                                recordVector
                        );

                /*
                 * For document-level RAG search,
                 * use a slightly lower threshold.
                 *
                 * We still want several context passages.
                 */
                if (similarity < 0.45) {
                    continue;
                }

                double score =
                        Math.round(
                                similarity * 1000.0
                        ) / 1000.0;

                Long bookId =
                        resolveBookId(
                                record,
                                bookIdMap
                        );

                results.add(
                        new SearchResultDTO(
                                record.getChunkId(),
                                record.getDocumentId(),
                                bookId,
                                record.getDocumentType(),
                                record.getTitle(),
                                record.getAuthor(),
                                record.getCategory(),
                                record.getSection(),
                                record.getChunkIndex() != null
                                        ? record.getChunkIndex()
                                        : 0,
                                record.getContent(),
                                score
                        )
                );

            } catch (Exception e) {

                logger.warn(
                        "Error parsing vector for document chunk {}: {}",
                        record.getChunkId(),
                        e.getMessage()
                );
            }
        }

        return results.stream()
                .sorted(
                        (r1, r2) ->
                                Double.compare(
                                        r2.getSimilarityScore(),
                                        r1.getSimilarityScore()
                                )
                )
                .limit(topK)
                .collect(Collectors.toList());
    }

    // =========================================================
    // HYBRID SEARCH
    // =========================================================

    public List<SearchResultDTO> performHybridSearch(
            String query,
            int topK,
            String documentType) {

        if (
                query == null ||
                query.isBlank()
        ) {
            return Collections.emptyList();
        }

        String normalizedQuery =
                query
                        .toLowerCase(Locale.ROOT)
                        .trim();

        String[] keywords =
                normalizedQuery
                        .split("[^a-z0-9]+");

        /*
         * Semantic search already:
         *
         * - removes weak matches
         * - removes duplicate books
         */
        List<SearchResultDTO> semanticResults =
                performSemanticSearch(
                        query,
                        Math.max(
                                topK * 3,
                                30
                        ),
                        documentType
                );

        for (
                SearchResultDTO result :
                semanticResults
        ) {

            double keywordScore =
                    0.0;

            int validKeywordCount =
                    0;

            String searchableText =
                    (
                            safe(result.getTitle())
                            + " "
                            + safe(result.getAuthor())
                            + " "
                            + safe(result.getCategory())
                            + " "
                            + safe(result.getSection())
                            + " "
                            + safe(result.getContent())
                    )
                    .toLowerCase(Locale.ROOT);

            for (String keyword : keywords) {

                if (
                        keyword != null &&
                        keyword.length() > 2
                ) {

                    validKeywordCount++;

                    if (
                            searchableText
                                    .contains(keyword)
                    ) {

                        keywordScore += 1.0;
                    }
                }
            }

            double normalizedKeywordScore =
                    validKeywordCount > 0
                            ? keywordScore
                            / validKeywordCount
                            : 0.0;

            double hybridScore =
                    (
                            0.7 *
                            result.getSimilarityScore()
                    )
                    +
                    (
                            0.3 *
                            Math.min(
                                    1.0,
                                    normalizedKeywordScore
                            )
                    );

            result.setSimilarityScore(
                    Math.round(
                            hybridScore * 1000.0
                    ) / 1000.0
            );
        }

        /*
         * Hybrid relevance can become lower after reranking.
         * Remove obviously weak hybrid results too.
         */
        return semanticResults
                .stream()
                .filter(
                        result ->
                                result.getSimilarityScore()
                                        >= 0.50
                )
                .sorted(
                        (r1, r2) ->
                                Double.compare(
                                        r2.getSimilarityScore(),
                                        r1.getSimilarityScore()
                                )
                )
                .limit(topK)
                .collect(Collectors.toList());
    }

    // =========================================================
    // UNIQUE RESOURCE KEY
    // =========================================================

    private String buildResourceKey(
            SearchResultDTO result) {

        if (result == null) {
            return UUID.randomUUID()
                    .toString();
        }

        String type =
                safe(
                        result.getDocumentType()
                )
                .toLowerCase(
                        Locale.ROOT
                );

        /*
         * BOOK
         *
         * Use catalogue book ID whenever available.
         */
        if (
                "book".equals(type) &&
                result.getBookId() != null
        ) {

            return "book:"
                    + result.getBookId();
        }

        /*
         * PAPER / DOCUMENT / fallback BOOK
         *
         * Use documentId.
         */
        if (
                result.getDocumentId() != null &&
                !result
                        .getDocumentId()
                        .isBlank()
        ) {

            return type
                    + ":"
                    + result
                        .getDocumentId()
                        .trim()
                        .toLowerCase(
                                Locale.ROOT
                        );
        }

        /*
         * Last-resort key.
         */
        return type
                + ":"
                + safe(
                        result.getTitle()
                )
                .trim()
                .toLowerCase(
                        Locale.ROOT
                );
    }

    // =========================================================
    // BOOK-ID MAPPING
    //
    // GUTENBERG-1661 -> 3003
    // =========================================================

    private Map<String, Long> buildBookResourceIdMap() {

        Map<String, Long> result =
                new HashMap<>();

        try {

            List<Book> books =
                    bookRepository.findAll();

            for (Book book : books) {

                if (
                        book == null ||
                        book.getId() == null
                ) {
                    continue;
                }

                String resourceId =
                        book.getResourceId();

                if (
                        resourceId != null &&
                        !resourceId.isBlank()
                ) {

                    result.put(
                            resourceId
                                    .trim()
                                    .toLowerCase(
                                            Locale.ROOT
                                    ),
                            book.getId()
                    );
                }

                /*
                 * Existing VectorIngestionService
                 * fallback format.
                 */
                result.put(
                        (
                                "BOOK-ID-"
                                + book.getId()
                        )
                        .toLowerCase(
                                Locale.ROOT
                        ),
                        book.getId()
                );
            }

        } catch (Exception e) {

            logger.warn(
                    "Could not build book resource map: {}",
                    e.getMessage()
            );
        }

        return result;
    }

    private Long resolveBookId(
            VectorRecord record,
            Map<String, Long> bookIdMap) {

        if (record == null) {
            return null;
        }

        if (
                record.getDocumentType() == null ||
                !"book".equalsIgnoreCase(
                        record.getDocumentType()
                )
        ) {
            return null;
        }

        String documentId =
                record.getDocumentId();

        if (
                documentId == null ||
                documentId.isBlank()
        ) {
            return null;
        }

        return bookIdMap.get(
                documentId
                        .trim()
                        .toLowerCase(
                                Locale.ROOT
                        )
        );
    }

    // =========================================================
    // COSINE SIMILARITY
    // =========================================================

    private double computeCosineSimilarity(
            float[] v1,
            float[] v2) {

        if (
                v1 == null ||
                v2 == null ||
                v1.length != v2.length
        ) {
            return 0.0;
        }

        double dotProduct =
                0.0;

        double normA =
                0.0;

        double normB =
                0.0;

        for (
                int i = 0;
                i < v1.length;
                i++
        ) {

            dotProduct +=
                    v1[i] * v2[i];

            normA +=
                    v1[i] * v1[i];

            normB +=
                    v2[i] * v2[i];
        }

        if (
                normA == 0.0 ||
                normB == 0.0
        ) {
            return 0.0;
        }

        return dotProduct /
                (
                        Math.sqrt(normA)
                        *
                        Math.sqrt(normB)
                );
    }

    private String safe(
            String value) {

        return value == null
                ? ""
                : value;
    }

    // =========================================================
    // SEARCH RESULT DTO
    // =========================================================

    public static class SearchResultDTO {

        private String chunkId;

        private String documentId;

        private Long bookId;

        private String documentType;

        private String title;

        private String author;

        private String category;

        private String section;

        private Integer chunkIndex;

        private String content;

        private double similarityScore;

        public SearchResultDTO() {}

        public SearchResultDTO(
                String chunkId,
                String documentId,
                Long bookId,
                String documentType,
                String title,
                String author,
                String category,
                String section,
                Integer chunkIndex,
                String content,
                double similarityScore) {

            this.chunkId =
                    chunkId;

            this.documentId =
                    documentId;

            this.bookId =
                    bookId;

            this.documentType =
                    documentType;

            this.title =
                    title;

            this.author =
                    author;

            this.category =
                    category;

            this.section =
                    section;

            this.chunkIndex =
                    chunkIndex;

            this.content =
                    content;

            this.similarityScore =
                    similarityScore;
        }

        public String getChunkId() {
            return chunkId;
        }

        public void setChunkId(
                String chunkId) {

            this.chunkId =
                    chunkId;
        }

        public String getDocumentId() {
            return documentId;
        }

        public void setDocumentId(
                String documentId) {

            this.documentId =
                    documentId;
        }

        public Long getBookId() {
            return bookId;
        }

        public void setBookId(
                Long bookId) {

            this.bookId =
                    bookId;
        }

        public String getDocumentType() {
            return documentType;
        }

        public void setDocumentType(
                String documentType) {

            this.documentType =
                    documentType;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(
                String title) {

            this.title =
                    title;
        }

        public String getAuthor() {
            return author;
        }

        public void setAuthor(
                String author) {

            this.author =
                    author;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(
                String category) {

            this.category =
                    category;
        }

        public String getSection() {
            return section;
        }

        public void setSection(
                String section) {

            this.section =
                    section;
        }

        public Integer getChunkIndex() {
            return chunkIndex;
        }

        public void setChunkIndex(
                Integer chunkIndex) {

            this.chunkIndex =
                    chunkIndex;
        }

        public String getContent() {
            return content;
        }

        public void setContent(
                String content) {

            this.content =
                    content;
        }

        public double getSimilarityScore() {
            return similarityScore;
        }

        public void setSimilarityScore(
                double similarityScore) {

            this.similarityScore =
                    similarityScore;
        }

        public double getScore() {
            return similarityScore;
        }
    }
}