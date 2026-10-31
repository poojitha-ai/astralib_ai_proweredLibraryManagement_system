package com.astralib.backend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Natural-language search that keeps two answers apart.
 *
 * A request for a book ("something easy for a beginner in Java") is answered
 * from catalogue metadata, because no passage of prose answers it. A request
 * for an explanation ("what is a closure") is answered from stored passages
 * with a citation back to the book. A query that asks for both gets both, but
 * they are returned in separate lists so a recommendation is never dressed up
 * as a search hit and a passage is never presented without its source.
 *
 * Both the Semantic and Smart entry points use this service. They used to be
 * different pipelines, and the semantic one had no query understanding and a
 * single 0.60 cosine cut, so a normal request like "new book to read and easy
 * to understand" was dropped before it could reach the catalogue. One
 * understood, calibrated pipeline now serves both.
 */
@Service
public class SmartSearchService {

    private static final Logger logger =
            LoggerFactory.getLogger(SmartSearchService.class);

    @Autowired
    private QueryUnderstandingService queryUnderstandingService;

    @Autowired
    private CatalogueRecommendationService catalogueRecommendationService;

    @Autowired
    private SemanticSearchService semanticSearchService;

    public Map<String, Object> search(
            String query,
            int topK,
            String documentType) {

        return search(query, topK, documentType, "smart");
    }

    public Map<String, Object> search(
            String query,
            int topK,
            String documentType,
            String mode) {

        long started = System.currentTimeMillis();

        QueryUnderstandingService.QueryPlan plan =
                queryUnderstandingService.plan(query);

        List<CatalogueRecommendationService.BookRecommendation> recommendations =
                new ArrayList<>();

        /*
         * "Readable" on a recommendation card means the reader can open the
         * stored edition, not that its vectors are loaded in this process.
         * Searchable passages are gated separately, so a freshly ingested
         * book is not advertised as unreadable while the index warms up.
         */

        if (plan.wantsRecommendation()) {
            recommendations = catalogueRecommendationService.recommend(
                    plan, topK, false
            );
        }

        /*
         * A pure "which book" question never touches the vector index: it is
         * answered from metadata alone, so it keeps working when the embedding
         * service is unavailable.
         */
        SemanticSearchService.PassageSearch found = null;

        List<SemanticSearchService.SearchResultDTO> passages =
                List.of();

        if (plan.wantsPassages()) {
            found = semanticSearchService.searchPassages(
                    query,
                    plan.retrievalQuery(),
                    topK,
                    documentType
            );

            passages = found.passages();
        }

        /*
         * A concept question the library cannot answer still deserves the
         * books that cover the area, if the catalogue has them. This is the
         * fallback the empty state never had.
         */
        if (passages.isEmpty() && recommendations.isEmpty()) {
            recommendations = catalogueRecommendationService.recommend(
                    plan, topK, false
            );
        }

        Map<String, Object> understanding = new LinkedHashMap<>();
        understanding.put("intent", plan.intent().name());
        understanding.put("summary", plan.summary());
        understanding.put("languages", plan.languages());
        understanding.put("topics", plan.topics());
        understanding.put("level", plan.level());
        understanding.put("author", plan.author());
        understanding.put("availability", plan.availability());
        understanding.put("programmingSubject", plan.programmingSubject());
        understanding.put("expandedQuery", plan.retrievalQuery());
        understanding.put("expansions", plan.expansions());

        /*
         * Evidence strength is about the source, not the ranking. A passage
         * that barely clears the floor is a weak citation even when it is the
         * best one available, and saying so is the difference between a
         * library answer and a plausible-looking guess.
         */
        double bestCosine = found == null ? 0.0 : found.bestSemantic();

        long weakPassages = passages.stream()
                .filter(passage -> passage.getSemanticScore() > 0.0)
                .filter(passage -> passage.getSemanticScore() < QueryEvidence.TRUSTED_SIMILARITY)
                .count();

        String evidence;

        if (found == null) {
            evidence = "not-required";
        } else if (passages.isEmpty()) {
            evidence = "none";
        } else if (bestCosine >= QueryEvidence.TRUSTED_SIMILARITY && weakPassages == 0) {
            evidence = "strong";
        } else {
            evidence = "partial";
        }

        /*
         * A topic can be asked about when the collection holds no book on it.
         * "TCP vs UDP" is the current example: an OS chapter mentions both,
         * but no networking text exists, so a Rust web-server page is not
         * networking evidence and must not be presented as if it were.
         */
        List<String> uncoveredTopics = new ArrayList<>();

        for (String topic : plan.topics()) {

            QueryUnderstandingService.QueryPlan probe =
                    new QueryUnderstandingService.QueryPlan(
                            QueryUnderstandingService.Intent.TOPIC_SEARCH,
                            List.of(),
                            List.of(topic),
                            null,
                            null,
                            null,
                            false,
                            topic,
                            List.of()
                    );

            boolean covered = catalogueRecommendationService
                    .recommend(probe, 3, false)
                    .stream()
                    .anyMatch(CatalogueRecommendationService.BookRecommendation::readable);

            if (!covered) {
                uncoveredTopics.add(topic);
            }
        }

        Map<String, Object> confidence = new LinkedHashMap<>();
        confidence.put("bestPassageScore", found == null ? null : found.bestSemantic());
        confidence.put("floor", found == null ? null : found.floor());
        confidence.put("candidates", found == null ? 0 : found.candidatesConsidered());
        confidence.put("embedding", found == null ? "not-required" : "ok");
        confidence.put("evidence", evidence);
        confidence.put("weakPassages", weakPassages);

        Map<String, Object> coverage = new LinkedHashMap<>();
        coverage.put("topics", plan.topics());
        coverage.put("uncoveredTopics", uncoveredTopics);
        coverage.put("hasDedicatedSource", uncoveredTopics.isEmpty());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("query", query);
        response.put("mode", mode);
        response.put("understanding", understanding);
        response.put("recommendations", recommendations);
        response.put("passages", passages);
        response.put("confidence", confidence);
        response.put("coverage", coverage);
        response.put("hasGroundedAnswer", !passages.isEmpty());

        String notice = buildNotice(
                plan, passages, recommendations, uncoveredTopics
        );

        if (notice != null) {
            response.put("notice", notice);
        }

        response.put(
                "elapsedMs",
                System.currentTimeMillis() - started
        );

        logger.info(
                "{} search '{}': intent {}, {} recommendations, {} passages, "
                        + "best cosine {} in {} ms",
                mode,
                query,
                plan.intent(),
                recommendations.size(),
                passages.size(),
                found == null ? "n/a" : found.bestSemantic(),
                System.currentTimeMillis() - started
        );

        return response;
    }

    /**
     * An explicit statement of what the library can and cannot answer is more
     * useful than an empty list. It is only added when there is something to
     * explain, so an ordinary result page stays quiet.
     */
    private String buildNotice(
            QueryUnderstandingService.QueryPlan plan,
            List<SemanticSearchService.SearchResultDTO> passages,
            List<CatalogueRecommendationService.BookRecommendation> recommendations,
            List<String> uncoveredTopics) {

        /*
         * Partial coverage is stated even when passages were found: the
         * reader is entitled to know that the collection has no book on the
         * subject and that what follows is incidental.
         */
        if (!uncoveredTopics.isEmpty() && !passages.isEmpty()) {
            return "AstraLib holds no dedicated "
                    + String.join(", ", uncoveredTopics).toLowerCase(java.util.Locale.ROOT)
                    + " textbook. The passages below are related material from other "
                    + "books, not a source on that subject.";
        }

        if (!passages.isEmpty()) {
            return null;
        }

        if (!recommendations.isEmpty()) {

            if (plan.wantsRecommendation()) {
                return "Matched from the catalogue, not from book text. Titles marked "
                        + "unavailable are catalogued but cannot be read yet.";
            }

            return "No indexed passage answers this question, so the catalogue is "
                    + "offered instead. Check a title's availability before relying "
                    + "on it for the answer.";
        }

        if (plan.wantsRecommendation()) {
            return "No catalogued book matches that language, level or author.";
        }

        return "No indexed book in AstraLib contains a passage that answers this "
                + "question, and no catalogue record covers the subject. Nothing "
                + "is invented to fill the gap.";
    }
}
