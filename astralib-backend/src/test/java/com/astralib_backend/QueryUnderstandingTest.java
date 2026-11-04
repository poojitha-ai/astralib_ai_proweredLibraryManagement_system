package com.astralib_backend;

import com.astralib.backend.PassageFilter;
import com.astralib.backend.QueryUnderstandingService;
import com.astralib.backend.VectorRecord;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rules that decide what a natural-language question is asking for, and
 * which stored rows may be shown as evidence.
 *
 * These are pure functions, so the test needs no database and no embedding
 * provider. They are asserted here because both mistakes are expensive: a
 * book request answered with a passage looks authoritative but says nothing,
 * and a title-block row cited as prose is a citation to text nobody can read.
 */
class QueryUnderstandingTest {

    private final QueryUnderstandingService service = new QueryUnderstandingService();

    @Test
    void bookRequestIsClassifiedAsRecommendation() {
        QueryUnderstandingService.QueryPlan plan =
                service.plan("Which Python book should I start with?");

        assertEquals(QueryUnderstandingService.Intent.BOOK_RECOMMENDATION, plan.intent());
        assertTrue(plan.wantsRecommendation());
        assertEquals(List.of("Python"), plan.languages());
        assertEquals("BEGINNER", plan.level());
    }

    @Test
    void vagueRequestForSomethingEasyToReadIsStillABookRequest() {
        QueryUnderstandingService.QueryPlan plan =
                service.plan("new book to read and easy to understand");

        assertEquals(QueryUnderstandingService.Intent.BOOK_RECOMMENDATION, plan.intent());
        assertTrue(plan.wantsRecommendation());
        assertEquals("BEGINNER", plan.level());
        assertTrue(plan.prefersReadable());

        /*
         * No passage of prose answers this, so retrieval must not be asked
         * for one (and must not fail when embeddings are unavailable).
         */
        assertFalse(plan.wantsPassages());
    }

    @Test
    void machineLearningTopicIsNotMistakenForLearningRequest() {
        QueryUnderstandingService.QueryPlan plan =
                service.plan("What is supervised learning?");

        assertEquals(QueryUnderstandingService.Intent.CONCEPT_SEARCH, plan.intent());
        assertFalse(plan.wantsRecommendation());
    }

    @Test
    void neuralNetworksAreNotClassifiedAsComputerNetworking() {
        QueryUnderstandingService.QueryPlan plan =
                service.plan("find information about neural networks");

        assertTrue(plan.topics().contains("Machine Learning"));
        assertFalse(plan.topics().contains("Networking"));
        assertEquals(QueryUnderstandingService.Intent.TOPIC_SEARCH, plan.intent());
    }

    @Test
    void authorIsExtractedFromAnAuthorRequest() {
        QueryUnderstandingService.QueryPlan plan =
                service.plan("books by Joshua Bloch");

        assertEquals(QueryUnderstandingService.Intent.AUTHOR_SEARCH, plan.intent());
        assertEquals("Joshua Bloch", plan.author());
        assertTrue(plan.wantsRecommendation());
    }

    @Test
    void advancedLevelIsDetected() {
        QueryUnderstandingService.QueryPlan plan =
                service.plan("I already know the basics, recommend a more advanced Rust book");

        assertEquals("ADVANCED", plan.level());
        assertTrue(plan.languages().contains("Rust"));
    }

    @Test
    void cPlusPlusDoesNotAlsoMatchPlainC() {
        QueryUnderstandingService.QueryPlan plan =
                service.plan("Best book to learn C++ from scratch?");

        assertEquals(List.of("C++"), plan.languages());
    }

    @Test
    void javaDoesNotMatchJavaScript() {
        QueryUnderstandingService.QueryPlan plan =
                service.plan("How do closures work in JavaScript?");

        assertEquals(List.of("JavaScript"), plan.languages());
    }

    @Test
    void conceptsExpandForRetrieval() {
        QueryUnderstandingService.QueryPlan plan =
                service.plan("What is a closure in JavaScript?");

        assertFalse(plan.expansions().isEmpty());
        assertTrue(plan.retrievalQuery().toLowerCase().contains("lexical scope"));
    }

    @Test
    void metadataStubIsNeverTreatedAsAPassage() {
        VectorRecord record = new VectorRecord();
        record.setContent(
                "Title: Fluent Python\n"
                        + "Author: Luciano Ramalho\n"
                        + "Description: Advanced Python techniques for experienced programmers."
        );
        record.setSection("full document");

        assertFalse(PassageFilter.isGroundedPassage(record));
    }

    @Test
    void realProseAndShortHeadingAreToldApart() {
        VectorRecord prose = new VectorRecord();
        prose.setContent(
                "A closure is a function that keeps a reference to the scope in which "
                        + "it was created, so it can still read the variables of that scope "
                        + "after the outer function has returned."
        );
        prose.setSection("Chapter 3: Functions");

        VectorRecord heading = new VectorRecord();
        heading.setContent("3.2 Closures");
        heading.setSection("Chapter 3: Functions");

        assertTrue(PassageFilter.isGroundedPassage(prose));
        assertFalse(PassageFilter.isGroundedPassage(heading));
    }
}
