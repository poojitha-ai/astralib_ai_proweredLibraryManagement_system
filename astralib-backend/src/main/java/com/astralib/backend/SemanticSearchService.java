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
     * Relevance floor for the raw vector engine (used by Hybrid Search, which
     * needs a candidate pool, and by the section-scoped lookups).
     *
     * This was 0.60, chosen by eye. Measured against this collection
     * (catalogue/calibrate_threshold.py) the best cosine for questions the
     * books really answer runs 0.525-0.797, so 0.60 discarded real answers
     * outright: "explain pointers with examples" scored 0.676 only after the
     * query was expanded, and the unexpanded phrasing fell below it. The
     * calibrated 0.46 keeps those and still rejects the unanswerable tail
     * (0.25-0.52). The user-facing Semantic path adds a relative floor and a
     * lexical branch on top; see SMART_ABSOLUTE_FLOOR below.
     */
    private static final double MIN_SEMANTIC_SCORE = 0.46;

    /*
     * RAG can use a slightly lower threshold because it needs
     * several relevant passages from the same resource.
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

    /*
     * Parsed embeddings and record metadata, built once per table change.
     * Searching 73k rows used to re-parse every embedding on every query.
     */
    @Autowired
    private VectorIndexService vectorIndexService;

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

        List<VectorRecord> records =
                vectorIndexService.records(
                        documentType
                );

        // Restrict retrieval when the query explicitly names a section.
String normalizedQuery = normalizeSectionForQuery(query);

/*
 * Normalised book titles, used to recognise front matter below.
 */
Set<String> normalizedTitles = records.stream()
        .map(VectorRecord::getTitle)
        .filter(Objects::nonNull)
        .map(this::normalizeSectionForQuery)
        .collect(Collectors.toSet());

Set<String> matchingSections = records.stream()
        .map(VectorRecord::getSection)
        .filter(Objects::nonNull)
        .distinct()
        .filter(section -> {
            String normalizedSection =
                    normalizeSectionForQuery(section);

            /*
             * A single common word is not a section reference: "explain
             * pointers with examples" used to narrow the whole search to the
             * chunks whose section was literally titled "Pointers", and the
             * better passages elsewhere were never scored. Only a multi-word
             * title ("Chapter 1. The Diamond") identifies a section.
             */
            if (normalizedSection.length() < 8
                    || !normalizedSection.contains(" ")
                    || !normalizedQuery.contains(normalizedSection)) {
                return false;
            }

            /*
             * A "section" that is the book's own title (or a prefix of
             * it) is the title page / licence / table of contents, not
             * a chapter. Gutenberg editions put that front matter in
             * one such section, so scoping to it when the query merely
             * mentions the book's title hides the real content and the
             * search returns nothing. Ignore title-like sections.
             */
            return normalizedTitles.stream()
                    .noneMatch(title -> title.startsWith(normalizedSection));
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
         * The best raw cosine is recorded even when it falls below the floor:
         * it is the number the threshold is calibrated against, and a query
         * that returns nothing is exactly when it needs to be visible.
         */
        double bestSeen = 0.0;
        int aboveFloor = 0;

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

            /*
             * A metadata-only row is a catalogue record, not a source.
             */
            if (!PassageFilter.isGroundedPassage(record)) {
                continue;
            }

            try {

                float[] recordVector =
                        vectorIndexService.vectorFor(record);

                if (recordVector == null) {
                    continue;
                }

                double similarity =
                        computeCosineSimilarity(
                                queryVector,
                                recordVector
                        );

                bestSeen = Math.max(bestSeen, similarity);

                /*
                 * Reject weak matches.
                 */
                if (
                        similarity <
                        MIN_SEMANTIC_SCORE
                ) {
                    continue;
                }

                aboveFloor++;

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

        List<SearchResultDTO> results = bestResultPerResource
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

        logger.info(
                "Semantic engine for '{}': best cosine {} against floor {}, "
                        + "{} chunks above floor, {} returned",
                query,
                Math.round(bestSeen * 1000.0) / 1000.0,
                MIN_SEMANTIC_SCORE,
                aboveFloor,
                results.size()
        );

        return results;
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

            /*
             * A metadata-only row is a catalogue record, not a source.
             */
            if (!PassageFilter.isGroundedPassage(record)) {
                continue;
            }

            try {

                float[] recordVector =
                        vectorIndexService.vectorFor(record);

                if (recordVector == null) {
                    continue;
                }

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

            /*
             * A metadata-only row is a catalogue record, not a source.
             */
            if (!PassageFilter.isGroundedPassage(record)) {
                continue;
            }

            try {

                float[] recordVector =
                        vectorIndexService.vectorFor(record);

                if (recordVector == null) {
                    continue;
                }

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

            if (!PassageFilter.isGroundedPassage(record)) {
                continue;
            }

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
    // SMART PASSAGE RETRIEVAL
    //
    // Used by Smart Search. Unlike performSemanticSearch it:
    //
    //  * never returns a metadata-only stub or a heading fragment
    //  * fuses semantic and lexical evidence after normalising each
    //    branch, so a keyword-only match is not diluted to nothing
    //  * cuts the tail relative to the best match instead of applying a
    //    single absolute cosine threshold
    //  * keeps the best passage per book
    //
    // The floors were calibrated against this collection, not chosen by eye
    // (catalogue/calibrate_threshold.py). Over 20 questions the books really
    // answer, the best raw cosine ran 0.525-0.797 (median 0.732); over 10
    // questions on subjects the library does not hold, it ran 0.250-0.520
    // (median 0.383). A 0.46 floor keeps every answerable question and rejects
    // 9 of the 10 unanswerable ones; 0.55 rejects all ten but also drops real
    // answers. The old 0.60 rejected real answers, and 0.26 let the
    // unanswerable tail through.
    //
    // The two ranges overlap between 0.46 and 0.52, so no single cut is exact:
    // one philosophy question still scores 0.52. The floor is set to prefer a
    // missed answer (the UI says the library holds no passage) over a cited
    // passage that does not answer the question.
    // =========================================================

    /** Below this a passage is noise even when it is the best available. */
    private static final double SMART_ABSOLUTE_FLOOR = 0.46;

    /*
     * A passage must score at least this share of the best passage.
     *
     * At 0.84 a single strong chunk starved the list: "what is inheritance in
     * Java" returned one book even though three Java texts explain it. 0.72
     * keeps the tail out while letting the second and third real answer
     * through (measured in catalogue/relevance_before/after.json).
     */
    private static final double SMART_RELATIVE_FLOOR = 0.72;


    /** Semantic weight in the fused passage score. */
    private static final double SMART_SEMANTIC_WEIGHT = 0.65;

    /** Lexical weight in the fused passage score. */
    private static final double SMART_KEYWORD_WEIGHT = 0.35;

    /**
     * What retrieval found, including the numbers behind the decision.
     *
     * The best raw cosine and the number of candidates are returned instead
     * of hidden in a log line, so a caller can explain why a query produced
     * no result rather than showing an unexplained empty state.
     */
    public record PassageSearch(
            List<SearchResultDTO> passages,
            double bestSemantic,
            int candidatesConsidered,
            double floor
    ) {
    }

    public List<SearchResultDTO> performSmartPassageSearch(
            String query,
            String retrievalQuery,
            int topK,
            String documentType) {

        return searchPassages(query, retrievalQuery, topK, documentType)
                .passages();
    }

    public PassageSearch searchPassages(
            String query,
            String retrievalQuery,
            int topK,
            String documentType) {

        if (
                query == null || query.isBlank()
        ) {
            return new PassageSearch(
                    Collections.emptyList(), 0.0, 0, SMART_ABSOLUTE_FLOOR
            );
        }

        String semanticQuery =
                retrievalQuery == null || retrievalQuery.isBlank()
                        ? query
                        : retrievalQuery;

        topK = Math.max(1, Math.min(topK, 50));

        float[] queryVector =
                embeddingService.generateEmbedding(semanticQuery);

        List<String> keywords =
                extractKeywordTerms(query);

        List<VectorRecord> records =
                vectorIndexService.records(documentType);

        List<Candidate> candidates = new ArrayList<>();

        double bestSemantic = 0.0;
        double bestKeyword = 0.0;

        Map<String, Long> bookIdMap =
                buildBookResourceIdMap();

        /*
         * Title, section and author repeat across a book's chunks, so their
         * token coverage is memoised for the duration of this query. Without
         * it every candidate re-normalises five fields and a query over the
         * full index takes seconds.
         */
        Map<String, Double> coverageCache = new HashMap<>();

        String normalizedQuery =
                normalizeKeywordText(query);

        for (VectorRecord record : records) {

            if (!PassageFilter.isGroundedPassage(record)) {
                continue;
            }

            float[] vector =
                    vectorIndexService.vectorFor(record);

            if (vector == null) {
                continue;
            }

            double similarity =
                    computeCosineSimilarity(queryVector, vector);

            /*
             * Cheap lexical pass first: a passage with no query term at all
             * and a weak embedding score cannot become a result.
             */
            double keywordScore = 0.0;

            if (!keywords.isEmpty()) {
                keywordScore = calculateKeywordScore(
                        vectorIndexService.normalizedFields(record),
                        normalizedQuery,
                        keywords,
                        coverageCache
                );
            }

            if (similarity < 0.15 && keywordScore < 0.20) {
                continue;
            }

            bestSemantic = Math.max(bestSemantic, similarity);
            bestKeyword = Math.max(bestKeyword, keywordScore);

            candidates.add(
                    new Candidate(record, similarity, keywordScore)
            );
        }

        if (candidates.isEmpty()) {
            return new PassageSearch(
                    Collections.emptyList(), bestSemantic, 0, SMART_ABSOLUTE_FLOOR
            );
        }

        /*
         * Fuse the two branches on their own scales. Multiplying a keyword
         * score by a fixed weight and comparing it with a cosine score is
         * what made hybrid search lose keyword-only answers.
         */
        for (Candidate candidate : candidates) {

            double semanticPart = bestSemantic > 0
                    ? candidate.semantic() / bestSemantic
                    : 0.0;

            double keywordPart = bestKeyword > 0
                    ? candidate.keyword() / bestKeyword
                    : 0.0;

            double fused =
                    SMART_SEMANTIC_WEIGHT * semanticPart
                            + SMART_KEYWORD_WEIGHT * keywordPart;

            if (sectionMatches(
                    vectorIndexService
                            .normalizedFields(candidate.record())[3],
                    keywords
            )) {
                fused += 0.05;
            }


            candidate.setFused(fused);
        }

        Map<String, Candidate> bestPerBook = new LinkedHashMap<>();

        for (Candidate candidate : candidates) {

            Long bookId = resolveBookId(candidate.record(), bookIdMap);

            String key = bookId != null
                    ? "BOOK-" + bookId
                    : safe(candidate.record().getDocumentId())
                            + "|"
                            + safe(candidate.record().getTitle());

            Candidate existing = bestPerBook.get(key);

            if (existing == null || candidate.fused() > existing.fused()) {
                bestPerBook.put(key, candidate);
            }
        }

        List<Candidate> ordered = new ArrayList<>(bestPerBook.values());

        ordered.sort(
                Comparator.comparingDouble(Candidate::fused).reversed()
        );

        double bestFused = ordered.get(0).fused();

        List<SearchResultDTO> results = new ArrayList<>();

        for (Candidate candidate : ordered) {

            if (results.size() >= topK) {
                break;
            }

            boolean strongEnough =
                    candidate.semantic() >= SMART_ABSOLUTE_FLOOR
                            && candidate.semantic()
                            >= bestSemantic * SMART_RELATIVE_FLOOR;

            boolean lexicallyStrong =
                    candidate.keyword() >= 0.45
                            && candidate.fused() >= bestFused * SMART_RELATIVE_FLOOR;

            if (!strongEnough && !lexicallyStrong) {
                continue;
            }

            VectorRecord record = candidate.record();

            /*
             * A weak match has to be about the question, not merely share a
             * generic word with it.
             */
            String haystack = String.join(
                    " ",
                    vectorIndexService.normalizedFields(record)
            );

            if (!QueryEvidence.isSupported(
                    candidate.semantic(),
                    haystack,
                    keywords
            )) {
                continue;
            }

            SearchResultDTO result = new SearchResultDTO(
                    record.getChunkId(),
                    record.getDocumentId(),
                    resolveBookId(record, bookIdMap),
                    record.getDocumentType(),
                    record.getTitle(),
                    record.getAuthor(),
                    record.getCategory(),
                    record.getSection(),
                    record.getChunkIndex() != null ? record.getChunkIndex() : 0,
                    record.getContent(),
                    roundScore(candidate.fused())
            );

            /*
             * The raw cosine is kept beside the fused relevance so a caller
             * can say how strong the evidence really is.
             */
            result.setSemanticScore(roundScore(candidate.semantic()));

            result.setWeakEvidence(
                    candidate.semantic() < QueryEvidence.TRUSTED_SIMILARITY
            );

            results.add(result);
        }

        /*
         * The raw best cosine is logged with the query: it is the number the
         * confidence floor is calibrated against, so it has to be observable
         * per query rather than inferred from the fused score.
         */
        logger.info(
                "Smart passages for '{}': {} candidates, best semantic {}, {} returned",
                query,
                candidates.size(),
                Math.round(bestSemantic * 1000.0) / 1000.0,
                results.size()
        );

        return new PassageSearch(
                results,
                Math.round(bestSemantic * 1000.0) / 1000.0,
                candidates.size(),
                SMART_ABSOLUTE_FLOOR
        );
    }

    private boolean sectionMatches(String section, List<String> keywords) {

        if (section == null || keywords == null || keywords.isEmpty()) {
            return false;
        }

        String normalized = normalizeKeywordText(section);
        int matches = 0;

        for (String keyword : keywords) {
            if (normalized.contains(keyword)) {
                matches++;
            }
        }

        return matches >= Math.max(1, keywords.size() / 2);
    }

    private static final class Candidate {

        private final VectorRecord record;
        private final double semantic;
        private final double keyword;
        private double fused;

        private Candidate(VectorRecord record, double semantic, double keyword) {
            this.record = record;
            this.semantic = semantic;
            this.keyword = keyword;
        }

        private VectorRecord record() {
            return record;
        }

        private double semantic() {
            return semantic;
        }

        private double keyword() {
            return keyword;
        }

        private double fused() {
            return fused;
        }

        private void setFused(double value) {
            this.fused = value;
        }
    }

    // =========================================================
    // KEYWORD / HYBRID HELPERS
    // =========================================================

    private List<VectorRecord> loadCandidateRecords(
            String documentType) {

        return vectorIndexService.records(
                documentType
        );
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

    /**
     * Lexical score over pre-normalised fields.
     *
     * Same weights as {@link #calculateKeywordScore(VectorRecord, String,
     * List)}, but it reads the cached normalised fields instead of
     * re-normalising title, author, category, section and content for every
     * record in the index.
     */
    private double calculateKeywordScore(
            String[] fields,
            String normalizedQuery,
            List<String> keywords,
            Map<String, Double> coverageCache) {

        String title = fields[0];
        String author = fields[1];
        String category = fields[2];
        String section = fields[3];
        String content = fields[4];

        double score =
                (
                        0.35 *
                        cachedCoverage(title, keywords, coverageCache)
                )
                +
                (
                        0.10 *
                        cachedCoverage(author, keywords, coverageCache)
                )
                +
                (
                        0.10 *
                        cachedCoverage(category, keywords, coverageCache)
                )
                +
                (
                        0.15 *
                        cachedCoverage(section, keywords, coverageCache)
                )
                +
                (
                        0.30 *
                        cachedCoverage(content, keywords, coverageCache)
                );

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

    private double cachedCoverage(
            String text,
            List<String> keywords,
            Map<String, Double> coverageCache) {

        if (text == null || text.isBlank()) {
            return 0.0;
        }

        Double cached = coverageCache.get(text);

        if (cached != null) {
            return cached;
        }

        double coverage = keywordCoverage(text, keywords);

        coverageCache.put(text, coverage);

        return coverage;
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

        /*
         * The fused score can exceed 1.0 because the section bonus is added
         * after normalisation. It is presented as a relevance figure between
         * 0 and 1, so it is clamped here rather than in the UI.
         */
        double bounded = Math.max(0.0, Math.min(1.0, value));

        return Math.round(
                bounded * 1000.0
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

        /**
         * Raw cosine for this passage, before fusion. The fused score ranks
         * results; this number says how strong the underlying evidence is.
         */
        private double semanticScore;

        /**
         * True when the passage barely clears the relevance floor.
         *
         * Fusion normalises the best candidate to a high score, so a weak
         * match can be displayed as "0.85" and read like strong evidence.
         * "TCP vs UDP" was the worst case: two loosely related pages were
         * shown beside a confident-looking number even though the collection
         * holds no networking text. The flag lets the reader see that the
         * passage is related material, not an answer.
         */
        private boolean weakEvidence;

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

        public double getSemanticScore() {
            return semanticScore;
        }

        public void setSemanticScore(
                double semanticScore) {

            this.semanticScore =
                    semanticScore;
        }

        public boolean isWeakEvidence() {
            return weakEvidence;
        }

        public void setWeakEvidence(
                boolean weakEvidence) {

            this.weakEvidence =
                    weakEvidence;
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