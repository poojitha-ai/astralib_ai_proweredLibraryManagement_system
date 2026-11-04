package com.astralib.backend;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a natural-language query before anything is retrieved.
 *
 * Two problems motivated this. First, readers type requests ("something easy
 * for a beginner in Java") that are catalogue questions, not passage
 * questions: no chunk of prose answers them, so they were returning nothing.
 * Second, short conceptual questions ("what is a closure?") embed too weakly
 * against long textbook passages, so the vocabulary of the question is
 * expanded with the terms the books themselves use.
 *
 * The query that goes to the vector index is never replaced: the original is
 * kept and the expansion is appended, so a bad expansion can only dilute a
 * match, never invent one.
 */
@Service
public class QueryUnderstandingService {

    public enum Intent {
        /** "recommend a beginner Java book" - answered from metadata. */
        BOOK_RECOMMENDATION,
        /** "what is a closure" - answered from passages. */
        CONCEPT_SEARCH,
        /** "how do I declare an array" - answered from passages. */
        PROCEDURAL,
        /** "find books about machine learning" - metadata, passages welcome. */
        TOPIC_SEARCH,
        /** "books by Joshua Bloch" - answered from metadata. */
        AUTHOR_SEARCH,
        /** "beginner programming books" - answered from metadata. */
        DIFFICULTY_SEARCH,
        /** everything else, treated as a topic lookup. */
        OTHER
    }

    /** Intent, detected entities and the query used for retrieval. */
    public record QueryPlan(
            Intent intent,
            List<String> languages,
            List<String> topics,
            String level,
            String author,
            String availability,
            boolean programmingSubject,
            String retrievalQuery,
            List<String> expansions
    ) {
        /**
         * Catalogue questions are answered from metadata, because no passage
         * of prose answers "which book should I read".
         */
        public boolean wantsRecommendation() {
            return intent == Intent.BOOK_RECOMMENDATION
                    || intent == Intent.TOPIC_SEARCH
                    || intent == Intent.AUTHOR_SEARCH
                    || intent == Intent.DIFFICULTY_SEARCH;
        }

        /** True when the reader asked for material they can open right now. */
        public boolean prefersReadable() {
            return "READABLE".equals(availability);
        }

        /**
         * A pure "which book" question is answered without touching the
         * vector index, so it keeps working when embeddings are unavailable.
         * A named subject still searches the text, because the reader asked
         * what the library says about it, not only what it holds.
         */
        public boolean wantsPassages() {
            if (!topics.isEmpty() || !expansions.isEmpty()) {
                return true;
            }

            return intent == Intent.CONCEPT_SEARCH
                    || intent == Intent.PROCEDURAL
                    || intent == Intent.OTHER;
        }

        public String summary() {
            StringBuilder text = new StringBuilder(intent.name().toLowerCase(Locale.ROOT));

            if (!languages.isEmpty()) {
                text.append("; language=").append(String.join("/", languages));
            }
            if (level != null) {
                text.append("; level=").append(level);
            }
            if (!topics.isEmpty()) {
                text.append("; topic=").append(String.join("/", topics));
            }
            if (author != null) {
                text.append("; author=").append(author);
            }
            if (availability != null) {
                text.append("; availability=").append(availability.toLowerCase(Locale.ROOT));
            }
            if (programmingSubject) {
                text.append("; subject=programming");
            }

            return text.toString();
        }
    }

    /*
     * Languages are matched on whole words so "c" does not fire inside
     * "closure" and "go" does not fire inside "algorithm".
     */
    private static final Map<String, String> LANGUAGES = new LinkedHashMap<>();

    static {
        LANGUAGES.put("c++", "C++");
        LANGUAGES.put("cpp", "C++");
        LANGUAGES.put("c#", "C#");
        LANGUAGES.put("csharp", "C#");
        LANGUAGES.put("javascript", "JavaScript");
        LANGUAGES.put("js", "JavaScript");
        LANGUAGES.put("typescript", "TypeScript");
        LANGUAGES.put("python", "Python");
        LANGUAGES.put("java", "Java");
        LANGUAGES.put("rust", "Rust");
        LANGUAGES.put("golang", "Go");
        LANGUAGES.put("go", "Go");
        LANGUAGES.put("c", "C");
    }

    /** Book-specific vocabulary for the concepts the collection covers. */
    private static final Map<String, String> EXPANSIONS = new LinkedHashMap<>();

    static {
        /*
         * "OOP" is an abbreviation the embedding model reads poorly on its
         * own, so the words the textbooks actually use are added. Without
         * this, "explain oop in simple language" matched a Rust section about
         * traits before the Java chapters that define classes and inheritance.
         */
        EXPANSIONS.put("oop", "object oriented programming class object inheritance polymorphism encapsulation");
        EXPANSIONS.put("object oriented", "object oriented programming class object inheritance polymorphism encapsulation");
        EXPANSIONS.put("closure", "closure lexical scope free variable");
        EXPANSIONS.put("pointer", "pointer memory address dereference");
        EXPANSIONS.put("inheritance", "inheritance superclass subclass override");
        EXPANSIONS.put("polymorphism", "polymorphism virtual method dynamic dispatch");
        EXPANSIONS.put("recursion", "recursion base case recursive call");
        EXPANSIONS.put("stack", "stack LIFO push pop");
        EXPANSIONS.put("queue", "queue FIFO enqueue dequeue");
        EXPANSIONS.put("linked list", "linked list node next pointer");
        EXPANSIONS.put("binary tree", "binary tree node left right traversal");
        EXPANSIONS.put("hash table", "hash table bucket collision");
        EXPANSIONS.put("sorting", "sorting quicksort mergesort comparison");
        EXPANSIONS.put("deadlock", "deadlock mutual exclusion hold and wait circular wait");
        EXPANSIONS.put("paging", "paging page table virtual address translation");
        EXPANSIONS.put("virtual memory", "virtual memory page fault address translation");
        EXPANSIONS.put("scheduler", "cpu scheduling ready queue time slice");
        EXPANSIONS.put("thread", "thread process concurrency context switch");
        EXPANSIONS.put("tcp", "TCP three-way handshake connection establishment");
        EXPANSIONS.put("socket", "socket connection port protocol");
        EXPANSIONS.put("ownership", "ownership borrowing lifetime reference");
        EXPANSIONS.put("borrowing", "borrowing reference lifetime borrow checker");
        EXPANSIONS.put("default argument", "default parameter value argument");
        EXPANSIONS.put("decorator", "decorator higher order function metaprogramming");
        EXPANSIONS.put("generator", "generator yield iterator laziness");
        EXPANSIONS.put("metaclass", "metaclass type creation metaprogramming");
        EXPANSIONS.put("array", "array index element");
        EXPANSIONS.put("string", "string character concatenation");
        EXPANSIONS.put("function", "function parameter return call");
        EXPANSIONS.put("loop", "loop iteration while for");
        EXPANSIONS.put("exception", "exception throw catch error handling");
        EXPANSIONS.put("interface", "interface abstract method implementation");
        EXPANSIONS.put("generic", "generic type parameter template");
        EXPANSIONS.put("memory", "memory allocation free address");
    }

    private static final Set<String> RECOMMENDATION_CUES = Set.of(
            "recommend", "recommendation", "recommendations", "suggest",
            "suggestion", "suggestions", "which book", "what book",
            "best book", "good book", "start with", "start learning",
            "getting started", "get started", "from scratch", "read next",
            "should i read", "should i start", "i want", "i'm new",
            "im new", "i am new", "new to", "beginner friendly",
            "beginner-friendly", "easy introduction", "gentle introduction",
            "something easy", "teach me", "learn", "learning path",
            "what should i read", "where do i start",
            /* "new book to read and easy to understand" asks for a book. */
            "book to read", "books to read", "something to read", "easy to understand",
            "easy to read", "book for", "books for", "book on", "books on",
            "find books", "find a book", "looking for a book", "need a book",
            "recommend me", "which language"
    );

    /**
     * Phrases that ask for a subject, not for a passage of prose. "Find books
     * about machine learning" wants the shelf, not an explanation.
     */
    private static final Set<String> TOPIC_CUES = Set.of(
            "find books about", "find books on", "find information about",
            "find information on", "books about", "books on", "book about",
            "material on", "material about", "resources on", "resources about",
            "literature on", "anything about"
    );

    private static final Set<String> AUTHOR_CUES = Set.of(
            "books by", "book by", "written by", "author "
    );

    /**
     * "to read", "available", "readable" - a request for something the reader
     * can open now rather than a catalogue record of it.
     */
    private static final Set<String> AVAILABILITY_CUES = Set.of(
            "to read", "available", "readable", "can i read", "to download",
            "full text", "full-text", "read now"
    );

    private static final Set<String> CONCEPT_CUES = Set.of(
            "what is", "what are", "what does", "define", "definition",
            "explain", "why does", "why is", "why do", "why are",
            "difference between", "how does", "how do", "how is",
            "how are", "when should", "meaning of"
    );

    /**
     * Phrases in which "learning" names a subject, not a wish to be taught.
     * Without this, "what is supervised learning" reads as a book request.
     */
    private static final Set<String> LEARNING_TOPICS = Set.of(
            "machine learning", "supervised learning", "unsupervised learning",
            "deep learning", "reinforcement learning", "transfer learning"
    );

    private static final Set<String> PROCEDURAL_CUES = Set.of(
            "how do i", "how to", "how can i", "steps to", "write a",
            "implement", "declare", "create a"
    );

    private static final Set<String> BEGINNER_WORDS = Set.of(
            "beginner", "beginners", "easy", "easiest", "first", "intro",
            "introduction", "introductory", "basic", "basics", "gentle",
            "from scratch", "new to", "starter", "simple",
            /* "which book should I start with" asks for a first book. */
            "start with", "should i start", "where do i start",
            "getting started", "get started"
    );

    private static final Set<String> ADVANCED_WORDS = Set.of(
            "advanced", "expert", "deep", "in depth", "in-depth",
            "metaprogramming", "internals", "mastery", "professional"
    );

    private static final Set<String> INTERMEDIATE_WORDS = Set.of(
            "intermediate", "next", "after learning", "beyond the basics",
            "more advanced"
    );

    public QueryPlan plan(String query) {

        String text = query == null ? "" : query.trim();
        String lower = text.toLowerCase(Locale.ROOT);

        List<String> languages = detectLanguages(lower);

        List<String> topics = detectTopics(lower);

        String level = detectLevel(lower);

        String author = detectAuthor(text, lower);

        String availability = containsAny(lower, AVAILABILITY_CUES)
                ? "READABLE"
                : null;

        Intent intent = detectIntent(lower, languages, topics, level, author);

        List<String> expansions = detectExpansions(lower);

        String retrievalQuery = buildRetrievalQuery(
                text, languages, expansions
        );

        /*
         * "programming book" is a subject filter, not a topic phrase: it
         * decides which shelf to consider, and it must not turn a metadata
         * request into a passage search.
         */
        boolean programmingSubject = lower.contains("programming")
                || containsWord(lower, "coding")
                || lower.contains("software engineering");

        return new QueryPlan(
                intent, languages, topics, level, author, availability,
                programmingSubject, retrievalQuery, expansions
        );
    }

    // =========================================================
    // INTENT
    // =========================================================

    private Intent detectIntent(
            String lower,
            List<String> languages,
            List<String> topics,
            String level,
            String author) {

        if (author != null) {
            return Intent.AUTHOR_SEARCH;
        }

        if (containsAny(lower, TOPIC_CUES)) {
            return Intent.TOPIC_SEARCH;
        }

        /*
         * A recommendation request can also look like a question ("which
         * book should I start with?"), so the recommendation cues are
         * checked before the concept cues. A bare "what is X" stays a
         * concept question.
         */
        String withoutTopics = lower;

        for (String topic : LEARNING_TOPICS) {
            withoutTopics = withoutTopics.replace(topic, " ");
        }

        boolean recommendation =
                containsAny(withoutTopics, RECOMMENDATION_CUES);

        if (recommendation
                && !containsAny(
                withoutTopics,
                Set.of("explain", "difference between", "what is", "what are")
        )) {
            return Intent.BOOK_RECOMMENDATION;
        }

        /*
         * "advanced programming books" - a level and a noun, no cue phrase.
         */
        if (level != null && containsAny(withoutTopics, Set.of("book", "books", "resource", "resources"))) {
            return Intent.DIFFICULTY_SEARCH;
        }

        if (containsAny(lower, PROCEDURAL_CUES)) {
            return Intent.PROCEDURAL;
        }

        if (containsAny(lower, CONCEPT_CUES)) {
            return Intent.CONCEPT_SEARCH;
        }

        /*
         * A named language or subject with no verb is a topic lookup.
         */
        if (!languages.isEmpty() || !topics.isEmpty()) {
            return Intent.TOPIC_SEARCH;
        }

        return Intent.OTHER;
    }

    /**
     * "books by Joshua Bloch" -> "Joshua Bloch". Only the words after the cue
     * are taken, so "books by Joshua Bloch about Java" still yields a name.
     */
    private String detectAuthor(String text, String lower) {

        for (String cue : AUTHOR_CUES) {

            int index = lower.indexOf(cue);

            if (index < 0) {
                continue;
            }

            String tail = text.substring(index + cue.length()).trim();

            if (tail.isEmpty()) {
                continue;
            }

            /* Stop at a preposition or punctuation. */
            String[] stop = tail.split("(?i)\\s+(?:about|on|for|in|with|who|that)\\b|[,.?;:()]");

            String candidate = stop[0].trim();

            if (candidate.length() >= 3
                    && candidate.split("\\s+").length <= 4) {
                return candidate;
            }
        }

        return null;
    }

    // =========================================================
    // ENTITIES
    // =========================================================

    private List<String> detectLanguages(String lower) {

        List<String> found = new ArrayList<>();

        for (Map.Entry<String, String> entry : LANGUAGES.entrySet()) {

            if (found.contains(entry.getValue())) {
                continue;
            }

            if (containsWord(lower, entry.getKey())) {
                found.add(entry.getValue());
            }
        }

        return found;
    }

    private List<String> detectTopics(String lower) {

        List<String> topics = new ArrayList<>();

        if (containsAny(lower, Set.of("algorithm", "algorithms", "sorting", "searching"))) {
            topics.add("Algorithms");
        }
        if (containsAny(lower, Set.of("data structure", "data structures", "linked list",
                "binary tree", "stack", "queue", "hash table", "array"))) {
            topics.add("Data Structures");
        }
        if (containsAny(lower, Set.of("object oriented", "object-oriented", "oop",
                "inheritance", "polymorphism", "encapsulation"))) {
            topics.add("Object-Oriented Programming");
        }
        /*
         * "neural network" is machine learning, not computer networking; a
         * plain substring test used to tag both.
         */
        String withoutNeuralNetworks = lower
                .replace("neural networks", " ")
                .replace("neural network", " ");

        if (containsAny(withoutNeuralNetworks, Set.of("network", "networking", "tcp",
                "udp", "socket", "protocol", "handshake"))) {
            topics.add("Networking");
        }
        if (containsAny(lower, Set.of("operating system", "kernel", "process", "thread",
                "scheduler", "deadlock", "paging", "virtual memory", "file system"))) {
            topics.add("Operating Systems");
        }
        if (containsAny(lower, Set.of("machine learning", "neural network", "gradient descent",
                "supervised learning", "classification", "regression", "deep learning"))) {
            topics.add("Machine Learning");
        }
        if (containsAny(lower, Set.of("database", "sql", "query", "index"))) {
            topics.add("Databases");
        }
        if (containsAny(lower, Set.of("web", "browser", "dom", "html", "css", "http"))) {
            topics.add("Web Development");
        }
        if (containsAny(lower, Set.of("concurrency", "concurrent", "parallel", "thread-safe"))) {
            topics.add("Concurrency");
        }

        return topics;
    }

    private String detectLevel(String lower) {

        if (containsAny(lower, ADVANCED_WORDS)) {
            return "ADVANCED";
        }
        if (containsAny(lower, INTERMEDIATE_WORDS)) {
            return "INTERMEDIATE";
        }
        if (containsAny(lower, BEGINNER_WORDS)) {
            return "BEGINNER";
        }

        return null;
    }

    private List<String> detectExpansions(String lower) {

        List<String> found = new ArrayList<>();

        for (Map.Entry<String, String> entry : EXPANSIONS.entrySet()) {
            if (lower.contains(entry.getKey())) {
                found.add(entry.getValue());
            }
        }

        return found;
    }

    private String buildRetrievalQuery(
            String original,
            List<String> languages,
            List<String> expansions) {

        StringBuilder query = new StringBuilder(original);

        /*
         * The language is usually present in a short query already, but
         * "how do I declare a pointer?" does not say C. When exactly one
         * language is named, repeating it helps the embedding agree with
         * passages that are full of that language's vocabulary.
         */
        if (languages.size() == 1) {
            query.append(' ').append(languages.get(0));
        }

        for (String expansion : expansions) {
            if (!query.toString().toLowerCase(Locale.ROOT).contains(expansion)) {
                query.append(' ').append(expansion);
            }
        }

        return query.toString();
    }

    // =========================================================
    // MATCHING HELPERS
    // =========================================================

    private boolean containsAny(String text, Set<String> needles) {

        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Whole-word match, so "c" does not match "closure" and "go" does not
     * match "algorithm".
     */
    private boolean containsWord(String text, String word) {

        if (text.equals(word)) {
            return true;
        }

        Matcher matcher = Pattern.compile(
                "(?<![a-z0-9+#])" + Pattern.quote(word) + "(?![a-z0-9+#])"
        ).matcher(text);

        return matcher.find();
    }

    /**
     * Distinct topics implied by a set of detected languages, used to keep
     * recommendations on subject.
     */
    public static Set<String> languageSubjects(List<String> languages) {
        return new LinkedHashSet<>(languages);
    }
}
