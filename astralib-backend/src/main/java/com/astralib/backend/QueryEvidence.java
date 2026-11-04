package com.astralib.backend;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Decides whether a weakly-scored passage is actually about the question.
 *
 * Cosine alone let unrelated text in: "explain quantum mechanics and wave
 * functions" matched a C++ page about printTime because both are about
 * "functions". The embedding model agreed weakly (0.498, just above the
 * floor), and the fused score then normalised that weak agreement up to 0.756,
 * so the page looked like a confident result.
 *
 * The guard is narrow on purpose. A passage scoring well semantically is never
 * second-guessed; only a weak match has to carry at least one word that
 * identifies the subject. Generic vocabulary ("function", "example", "book")
 * does not identify a subject, so it cannot carry a weak match on its own.
 */
public final class QueryEvidence {

    /** At or above this cosine a match speaks for itself. */
    public static final double TRUSTED_SIMILARITY = 0.58;

    private static final Set<String> GENERIC_TERMS = Set.of(
            "function", "functions", "method", "methods",
            "example", "examples", "sample", "samples",
            "explain", "explains", "explanation", "define", "definition",
            "work", "works", "working", "use", "used", "uses", "using",
            "simple", "simply", "easy", "hard", "basic", "basics",
            "language", "languages", "program", "programs", "programming",
            "code", "coding", "book", "books", "read", "reading",
            "learn", "learning", "understand", "understanding",
            "difference", "differences", "between", "compare", "comparison",
            "concept", "concepts", "introduce", "introduction",
            "start", "starting", "beginner", "beginners", "advanced",
            "help", "best", "good", "great", "way", "ways",
            "something", "thing", "things", "type", "types",
            "value", "values", "chapter", "page", "pages", "section",
            "information", "about", "tell", "show", "give", "with",
            "write", "writes", "create", "creates", "make", "makes"
    );

    private QueryEvidence() {
    }

    /**
     * @param haystack title, section, author and content, already normalised
     *                 to lower case
     * @param keywords meaningful query terms
     */
    public static boolean hasDistinctiveTerm(String haystack, List<String> keywords) {

        if (haystack == null || keywords == null || keywords.isEmpty()) {
            return false;
        }

        String text = haystack.toLowerCase(Locale.ROOT);

        for (String keyword : keywords) {

            if (keyword == null || keyword.isBlank() || GENERIC_TERMS.contains(keyword)) {
                continue;
            }

            if (text.contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    /**
     * A weak cosine needs distinctive support; a confident one does not.
     */
    public static boolean isSupported(
            double similarity,
            String haystack,
            List<String> keywords) {

        if (similarity >= TRUSTED_SIMILARITY) {
            return true;
        }

        return hasDistinctiveTerm(haystack, keywords);
    }
}
