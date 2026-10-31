package com.astralib_backend;

import com.astralib.backend.EmbeddingService;
import com.astralib.backend.SemanticSearchService;
import com.astralib.backend.VectorRecord;
import com.astralib.backend.VectorRecordRepository;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Regression test for section-scoped retrieval.
 *
 * Gutenberg editions put the licence, title page and table of contents
 * into a "section" named after the book itself. A question that merely
 * mentions the book's title used to match that section, narrow the
 * search to a handful of front-matter chunks and return nothing.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SectionScopeSearchTest extends AbstractIntegrationTest {

    private static final int DIMENSIONS = 384;

    @Autowired
    private SemanticSearchService semanticSearchService;

    @Autowired
    private VectorRecordRepository vectorRecordRepository;

    @MockitoBean
    private EmbeddingService embeddingService;

    private float[] queryVector;

    @BeforeAll
    void seedVectors() {

        /*
         * The mocked provider returns one fixed vector for every query.
         * The chapter chunk carries the same vector, so it scores 1.0;
         * the front-matter chunk is orthogonal and scores 0.
         */
        queryVector = new float[DIMENSIONS];
        queryVector[0] = 1.0f;

        float[] frontMatterVector = new float[DIMENSIONS];
        frontMatterVector[1] = 1.0f;

        String docId = "SCOPE-TEST-DOC";

        vectorRecordRepository.save(new VectorRecord(
                docId + "-CHK-00000",
                docId,
                "book",
                "Front Matter Book",
                "Test Author",
                "Classic Literature",
                "Front Matter Book",
                0,
                "The Project Gutenberg eBook of Front Matter Book. This eBook is for the use of anyone anywhere.",
                toJson(frontMatterVector)
        ));

        vectorRecordRepository.save(new VectorRecord(
                docId + "-CHK-00001",
                docId,
                "book",
                "Front Matter Book",
                "Test Author",
                "Classic Literature",
                "Chapter 1. The Diamond",
                1,
                "The detective examined the diamond closely and then explained how the theft had been committed.",
                toJson(queryVector)
        ));
    }

    /*
     * Spring resets the mock after every test method, so the stub has to
     * be installed for each one.
     */
    @BeforeEach
    void stubEmbeddingProvider() {
        when(embeddingService.generateEmbedding(anyString())).thenReturn(queryVector);
        when(embeddingService.getDimension()).thenReturn(DIMENSIONS);
    }

    private static String toJson(float[] vector) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(vector[i]);
        }
        return builder.append(']').toString();
    }

    @Test
    void queryMentioningTheBookTitleStillReachesTheChapterContent() {

        List<SemanticSearchService.SearchResultDTO> results =
                semanticSearchService.performSemanticSearch(
                        "Who took the diamond in Front Matter Book?",
                        3,
                        null
                );

        assertFalse(
                results.isEmpty(),
                "A title-named front-matter section must not hide the book's content"
        );

        assertEquals("Chapter 1. The Diamond", results.get(0).getSection());
        assertEquals("SCOPE-TEST-DOC", results.get(0).getDocumentId());
        assertTrue(results.get(0).getSimilarityScore() > 0.9);
    }

    @Test
    void namingARealChapterStillScopesTheSearch() {

        List<SemanticSearchService.SearchResultDTO> results =
                semanticSearchService.performSemanticSearch(
                        "the diamond in Chapter 1. The Diamond",
                        3,
                        null
                );

        assertEquals(1, results.size());
        assertEquals("Chapter 1. The Diamond", results.get(0).getSection());
    }
}
