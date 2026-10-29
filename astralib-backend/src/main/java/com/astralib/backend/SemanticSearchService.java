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

    /*
     * Hybrid Search uses two independent candidate sources:
     *
     * 1. semantic vector retrieval
     * 2. lexical keyword retrieval
     *
     * These weights can later be tuned using the research-paper
     * evaluation dataset.
     */
    private static final double HYBRID_SEMANTIC_WEIGHT = 0.60;
    private static final double HYBRID_KEYWORD_WEIGHT = 0.40;

    /*
     * Keyword-only candidates must be allowed into Hybrid Search,
     * otherwise Hybrid would collapse back into Semantic Search.
     */
    private static final double MIN_KEYWORD_SCORE = 0.08;
    private static final double MIN_HYBRID_SCORE = 0.20;

    /*
     * Very common words do not help lexical ranking.
     */
    private static final Set<String> KEYWORD_STOP_WORDS =
            Set.of(
                    "a", "an", "the", "and", "or", "but",
                    "of", "to", "in", "on", "for", "with",
                    "by", "from", "at", "as", "is", "are",
                    "was", "were", "be", "been", "being",
                    "this", "that", "these", "those", "through"
            );

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
        // Restrict retrieval when the query explicitly names a section.
String normalizedQuery = normalizeSectionForQuery(query);

Set<String> matchingSections = records.stream()
        .map(VectorRecord::getSection)
        .filter(Objects::nonNull)
        .distinct()
        .filter(section -> {
            String normalizedSection =
                    normalizeSectionForQuery(section);

            return normalizedSection.length() >= 8
                    && normalizedQuery.contains(normalizedSection);
        })
        .collect(Collectors.toSet());

if (!matchingSections.isEmpty()) {
    List<VectorRecord> sectionRecords = records.stream()
            .filter(record ->
                    matchingSections.contains(record.getSection()))
            .collect(Collectors.toList());

    if (!sectionRecords.isEmpty()) {
        records = sectionRecords;

        logger.info(
                "RAG section scope matched {}. Searching {} chunks.",
                matchingSections,
                records.size()
        );
    }
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
    // KEYWORD SEARCH
    //
    // IMPORTANT:
    // This is an INDEPENDENT lexical retrieval path.
    //
    // It does NOT call performSemanticSearch() and does NOT use
    // embeddings. It scans the textual fields already stored in
    // vector_records and keeps the best matching chunk for each
    // unique resource.
    // =========================================================

    public List<SearchResultDTO> performKeywordSearch(
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

        List<String> keywords =
                extractKeywordTerms(
                        query
                );

        if (
                keywords.isEmpty()
        ) {
            return Collections.emptyList();
        }

        String normalizedQuery =
                normalizeKeywordText(
                        query
                );

        List<VectorRecord> records =
                loadCandidateRecords(
                        documentType
                );

        Map<String, Long> bookIdMap =
                buildBookResourceIdMap();

        Map<String, SearchResultDTO> bestResultPerResource =
                new LinkedHashMap<>();

        for (
                VectorRecord record :
                records
        ) {

            double keywordScore =
                    calculateKeywordScore(
                            record,
                            normalizedQuery,
                            keywords
                    );

            if (
                    keywordScore <
                    MIN_KEYWORD_SCORE
            ) {
                continue;
            }

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
                            roundScore(
                                    keywordScore
                            )
                    );

            String resourceKey =
                    buildResourceKey(
                            candidate
                    );

            SearchResultDTO existing =
                    bestResultPerResource
                            .get(
                                    resourceKey
                            );

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
    // TRUE HYBRID SEARCH
    //
    // OLD BEHAVIOUR:
    // semantic results -> keyword reranking
    //
    // NEW BEHAVIOUR:
    //
    // semantic candidates --------\
    //                               -> UNION -> weighted fusion
    // keyword candidates ----------/
    //
    // This means a resource discovered ONLY by keyword search can
    // now enter the final Hybrid result set.
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

        topK =
                Math.max(
                        1,
                        Math.min(topK, 100)
                );

        int candidateLimit =
                Math.min(
                        100,
                        Math.max(
                                topK * 5,
                                30
                        )
                );

        // -----------------------------------------------------
        // Branch 1: semantic retrieval
        // -----------------------------------------------------

        List<SearchResultDTO> semanticResults =
                performSemanticSearch(
                        query,
                        candidateLimit,
                        documentType
                );

        // -----------------------------------------------------
        // Branch 2: independent keyword retrieval
        // -----------------------------------------------------

        List<SearchResultDTO> keywordResults =
                performKeywordSearch(
                        query,
                        candidateLimit,
                        documentType
                );

        Map<String, SearchResultDTO> candidateByResource =
                new LinkedHashMap<>();

        Map<String, Double> semanticScoreByResource =
                new HashMap<>();

        Map<String, Double> keywordScoreByResource =
                new HashMap<>();

        // -----------------------------------------------------
        // Add semantic candidates
        // -----------------------------------------------------

        for (
                SearchResultDTO result :
                semanticResults
        ) {

            String key =
                    buildResourceKey(
                            result
                    );

            semanticScoreByResource.put(
                    key,
                    result.getSimilarityScore()
            );

            candidateByResource.put(
                    key,
                    copySearchResult(
                            result
                    )
            );
        }

        // -----------------------------------------------------
        // Add keyword candidates independently
        // -----------------------------------------------------

        for (
                SearchResultDTO result :
                keywordResults
        ) {

            String key =
                    buildResourceKey(
                            result
                    );

            keywordScoreByResource.put(
                    key,
                    result.getSimilarityScore()
            );

            SearchResultDTO current =
                    candidateByResource
                            .get(
                                    key
                            );

            if (
                    current == null
            ) {

                /*
                 * Keyword-only candidate.
                 *
                 * This is the key difference from the old
                 * implementation.
                 */
                candidateByResource.put(
                        key,
                        copySearchResult(
                                result
                        )
                );

            } else {

                /*
                 * If both branches found the same resource,
                 * display whichever chunk contributes more to
                 * the final weighted score.
                 */
                double semanticContribution =
                        HYBRID_SEMANTIC_WEIGHT
                        *
                        semanticScoreByResource
                                .getOrDefault(
                                        key,
                                        0.0
                                );

                double keywordContribution =
                        HYBRID_KEYWORD_WEIGHT
                        *
                        result.getSimilarityScore();

                if (
                        keywordContribution
                                >
                        semanticContribution
                ) {

                    candidateByResource.put(
                            key,
                            copySearchResult(
                                    result
                            )
                    );
                }
            }
        }

        List<SearchResultDTO> fusedResults =
                new ArrayList<>();

        // -----------------------------------------------------
        // Fuse independent scores
        // -----------------------------------------------------

        for (
                Map.Entry<String, SearchResultDTO> entry :
                candidateByResource.entrySet()
        ) {

            String key =
                    entry.getKey();

            SearchResultDTO result =
                    entry.getValue();

            double semanticScore =
                    semanticScoreByResource
                            .getOrDefault(
                                    key,
                                    0.0
                            );

            double keywordScore =
                    keywordScoreByResource
                            .getOrDefault(
                                    key,
                                    0.0
                            );

            double hybridScore =
                    (
                            HYBRID_SEMANTIC_WEIGHT
                            *
                            semanticScore
                    )
                    +
                    (
                            HYBRID_KEYWORD_WEIGHT
                            *
                            keywordScore
                    );

            hybridScore =
                    roundScore(
                            hybridScore
                    );

            if (
                    hybridScore <
                    MIN_HYBRID_SCORE
            ) {
                continue;
            }

            result.setSimilarityScore(
                    hybridScore
            );

            fusedResults.add(
                    result
            );
        }

        return fusedResults
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
    // KEYWORD / HYBRID HELPERS
    // =========================================================

    private List<VectorRecord> loadCandidateRecords(
            String documentType) {

        if (
                documentType != null &&
                !documentType.isBlank() &&
                !"all".equalsIgnoreCase(
                        documentType
                )
        ) {

            return vectorRecordRepository
                    .findByDocumentType(
                            documentType
                                    .toLowerCase(
                                            Locale.ROOT
                                    )
                    );
        }

        return vectorRecordRepository
                .findAll();
    }

    private List<String> extractKeywordTerms(
            String query) {

        if (
                query == null ||
                query.isBlank()
        ) {
            return Collections.emptyList();
        }

        String normalized =
                normalizeKeywordText(
                        query
                );

        if (
                normalized.isBlank()
        ) {
            return Collections.emptyList();
        }

        List<String> terms =
                Arrays.stream(
                        normalized.split("\\s+")
                )
                .filter(
                        token ->
                                token != null &&
                                token.length() > 2
                )
                .filter(
                        token ->
                                !KEYWORD_STOP_WORDS
                                        .contains(
                                                token
                                        )
                )
                .distinct()
                .collect(
                        Collectors.toList()
                );

        /*
         * If the query contains only short/common words,
         * keep the normalized non-empty tokens as a fallback.
         */
        if (
                terms.isEmpty()
        ) {

            terms =
                    Arrays.stream(
                            normalized.split("\\s+")
                    )
                    .filter(
                            token ->
                                    token != null &&
                                    !token.isBlank()
                    )
                    .distinct()
                    .collect(
                            Collectors.toList()
                    );
        }

        return terms;
    }

    private double calculateKeywordScore(
            VectorRecord record,
            String normalizedQuery,
            List<String> keywords) {

        if (
                record == null ||
                keywords == null ||
                keywords.isEmpty()
        ) {
            return 0.0;
        }

        String title =
                normalizeKeywordText(
                        record.getTitle()
                );

        String author =
                normalizeKeywordText(
                        record.getAuthor()
                );

        String category =
                normalizeKeywordText(
                        record.getCategory()
                );

        String section =
                normalizeKeywordText(
                        record.getSection()
                );

        String content =
                normalizeKeywordText(
                        record.getContent()
                );

        /*
         * Weighted token coverage.
         *
         * Title and content carry most of the lexical evidence.
         */
        double score =
                (
                        0.35 *
                        keywordCoverage(
                                title,
                                keywords
                        )
                )
                +
                (
                        0.10 *
                        keywordCoverage(
                                author,
                                keywords
                        )
                )
                +
                (
                        0.10 *
                        keywordCoverage(
                                category,
                                keywords
                        )
                )
                +
                (
                        0.15 *
                        keywordCoverage(
                                section,
                                keywords
                        )
                )
                +
                (
                        0.30 *
                        keywordCoverage(
                                content,
                                keywords
                        )
                );

        /*
         * Exact phrase bonuses.
         *
         * These are useful for title searches such as:
         * "Pride and Prejudice"
         * "Adventures"
         */
        if (
                normalizedQuery != null &&
                !normalizedQuery.isBlank()
        ) {

            if (
                    containsKeywordPhrase(
                            title,
                            normalizedQuery
                    )
            ) {
                score += 0.25;
            }

            if (
                    containsKeywordPhrase(
                            section,
                            normalizedQuery
                    )
            ) {
                score += 0.10;
            }

            if (
                    containsKeywordPhrase(
                            content,
                            normalizedQuery
                    )
            ) {
                score += 0.10;
            }

            if (
                    containsKeywordPhrase(
                            author,
                            normalizedQuery
                    )
            ) {
                score += 0.05;
            }

            if (
                    containsKeywordPhrase(
                            category,
                            normalizedQuery
                    )
            ) {
                score += 0.05;
            }
        }

        return Math.min(
                1.0,
                score
        );
    }

    private double keywordCoverage(
            String text,
            List<String> keywords) {

        if (
                text == null ||
                text.isBlank() ||
                keywords == null ||
                keywords.isEmpty()
        ) {
            return 0.0;
        }

        int matches =
                0;

        String paddedText =
                " "
                + text
                + " ";

        for (
                String keyword :
                keywords
        ) {

            if (
                    keyword == null ||
                    keyword.isBlank()
            ) {
                continue;
            }

            String paddedKeyword =
                    " "
                    + keyword
                    + " ";

            if (
                    paddedText.contains(
                            paddedKeyword
                    )
            ) {
                matches++;
            }
        }

        return (
                double
        ) matches
                /
                keywords.size();
    }

    private boolean containsKeywordPhrase(
            String text,
            String normalizedQuery) {

        return text != null &&
                !text.isBlank() &&
                normalizedQuery != null &&
                !normalizedQuery.isBlank() &&
                text.contains(
                        normalizedQuery
                );
    }

    private String normalizeKeywordText(
            String value) {

        if (
                value == null
        ) {
            return "";
        }

        return value
                .toLowerCase(
                        Locale.ROOT
                )
                .replaceAll(
                        "[^a-z0-9]+",
                        " "
                )
                .trim()
                .replaceAll(
                        "\\s+",
                        " "
                );
    }
    private String normalizeSectionForQuery(String value) {
    if (value == null) {
        return "";
    }

    String normalized = normalizeKeywordText(value);

    // Remove a leading Roman numeral from section labels.
    return normalized
            .replaceFirst(
                    "^(?:chapter\\s+)?[ivxlcdm]+\\s+",
                    ""
            )
            .trim();
}
    private SearchResultDTO copySearchResult(
            SearchResultDTO source) {

        if (
                source == null
        ) {
            return null;
        }

        return new SearchResultDTO(
                source.getChunkId(),
                source.getDocumentId(),
                source.getBookId(),
                source.getDocumentType(),
                source.getTitle(),
                source.getAuthor(),
                source.getCategory(),
                source.getSection(),
                source.getChunkIndex(),
                source.getContent(),
                source.getSimilarityScore()
        );
    }

    private double roundScore(
            double value) {

        return Math.round(
                value * 1000.0
        ) / 1000.0;
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