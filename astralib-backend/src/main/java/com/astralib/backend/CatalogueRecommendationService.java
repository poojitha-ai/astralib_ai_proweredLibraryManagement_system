package com.astralib.backend;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Answers "which book should I read?" from catalogue metadata.
 *
 * This deliberately does not use embeddings. A reader asking for an easy Java
 * book needs the catalogue's own record of difficulty and language, and a
 * book that is catalogued but not yet digitised still has a correct answer
 * ("intermediate Java, not available to read yet"); it must not be offered as
 * if it were searchable, and it must not be hidden either.
 */
@Service
public class CatalogueRecommendationService {

    private static final int MAX_RECOMMENDATIONS = 6;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private PathwayBookRepository pathwayBookRepository;

    @Autowired
    private VectorIndexService vectorIndexService;

    /** One suggested book, with an explicit statement of what it can do. */
    public record BookRecommendation(
            Long bookId,
            String title,
            String author,
            String category,
            String language,
            String level,
            boolean readable,
            String format,
            String reason
    ) {
    }

    public List<BookRecommendation> recommend(
            QueryUnderstandingService.QueryPlan plan,
            int limit) {

        return recommend(plan, limit, true);
    }

    /**
     * @param requireIndexed when true a book counts as readable only when its
     *        passages are in the vector index (Smart Search behaviour). The
     *        chat passes false: it is recommending something to open, and a
     *        stored edition is openable whether or not its vectors are loaded
     *        in this process yet.
     */
    @Transactional(readOnly = true)
    public List<BookRecommendation> recommend(
            QueryUnderstandingService.QueryPlan plan,
            int limit,
            boolean requireIndexed) {

        if (plan == null) {
            return List.of();
        }

        int wanted = Math.min(
                Math.max(limit, 1),
                MAX_RECOMMENDATIONS
        );

        Set<String> languages = new HashSet<>(plan.languages());
        Set<String> topics = new HashSet<>(plan.topics());

        if (languages.isEmpty()
                && topics.isEmpty()
                && plan.level() == null
                && plan.author() == null) {
            return List.of();
        }

        Map<Long, List<PathwayBook>> placements = new HashMap<>();

        for (PathwayBook membership : pathwayBookRepository.findAll()) {
            if (membership.getBook() == null) {
                continue;
            }
            placements.computeIfAbsent(
                    membership.getBook().getId(),
                    key -> new ArrayList<>()
            ).add(membership);
        }

        Set<String> indexedDocuments = requireIndexed
                ? indexedDocumentIds()
                : Set.of();

        List<Book> catalogue = bookRepository.findAll();

        /*
         * Whether the requested level is actually served by a readable book
         * decides the wording of the lower note: "no readable beginner title
         * exists" is false once one is recommended above it.
         */
        boolean readableAtRequestedLevel = plan.level() != null
                && catalogue.stream()
                        .filter(book -> placements.containsKey(book.getId()))
                        .filter(book -> plan.level().equalsIgnoreCase(
                                levelOf(placements.get(book.getId()))))
                        .anyMatch(book -> requireIndexed
                                ? isReadable(book, indexedDocuments)
                                : isOpenable(book));

        List<Scored> candidates = new ArrayList<>();

        for (Book book : catalogue) {

            /*
             * Only curated titles take part: the legacy imported rows are
             * catalogue noise here and would bury the books a reader can
             * actually choose between.
             */
            if (!placements.containsKey(book.getId())) {
                continue;
            }

            double score = 0.0;
            List<String> reasons = new ArrayList<>();

            String language = bookLanguage(book);

            /*
             * "books by Joshua Bloch" - the author is part of the question,
             * so a title by someone else is not a weaker answer, it is the
             * wrong answer.
             */
            if (plan.author() != null) {

                String name = book.getAuthor() == null ? null : book.getAuthor().getName();

                if (name == null
                        || !name.toLowerCase(Locale.ROOT)
                        .contains(plan.author().toLowerCase(Locale.ROOT))) {
                    continue;
                }

                score += 4.0;
                reasons.add("by " + name);
            }

            if (!languages.isEmpty()) {
                if (language != null && languages.contains(language)) {
                    score += 6.0;
                    reasons.add(language + " programming");
                } else if (matchesAnyLanguage(book, languages)) {
                    score += 4.0;
                    reasons.add("covers " + String.join("/", languages));
                } else {
                    continue;
                }
            }

            if (!topics.isEmpty()) {

                boolean topicMatch = false;

                for (String topic : topics) {
                    if (bookMatchesTopic(book, topic)) {
                        score += 3.0;
                        reasons.add(topic.toLowerCase(Locale.ROOT));
                        topicMatch = true;
                    }
                }

                if (!topicMatch && languages.isEmpty()) {
                    continue;
                }
            }

            if (plan.programmingSubject() && !isProgrammingBook(book)) {
                continue;
            }

            if (languages.isEmpty() && topics.isEmpty()) {
                score += 1.0;
            }

            String level = levelOf(placements.get(book.getId()));

            int distance = levelDistance(plan.level(), level);

            if (distance == 0) {
                score += 5.0;
                reasons.add("matches the " + level.toLowerCase(Locale.ROOT) + " level asked for");
            } else if (distance > 0) {
                score -= distance;
            }

            boolean readable = requireIndexed
                    ? isReadable(book, indexedDocuments)
                    : isOpenable(book);

            score += readable ? 1.5 : 0.0;

            String format = formatOf(book);

            if (reasons.isEmpty()) {
                reasons.add(book.getCategory() != null
                        ? book.getCategory().getName()
                        : "catalogue match");
            }

            /*
             * A level the reader did not ask for is only acceptable when it is
             * stated. An advanced title offered to a beginner is exactly the
             * case that needs a sentence, not a silently better-looking list.
             */
            String levelNote = levelNote(
                    plan.level(),
                    level,
                    readable,
                    language,
                    readableAtRequestedLevel
            );

            if (levelNote != null) {
                reasons.add(levelNote);
            }

            /*
             * The level note already says "catalogued but not available to
             * read" for a mismatched level, so appending the availability
             * sentence as well read as "catalogued but not available to read,
             * not available to read yet".
             */
            if (!readable
                    && reasons.stream().noneMatch(reason ->
                            reason.toLowerCase(Locale.ROOT).contains("not available"))) {
                reasons.add("not available to read yet");
            }

            candidates.add(
                    new Scored(
                            new BookRecommendation(
                                    book.getId(),
                                    book.getTitle(),
                                    book.getAuthor() == null
                                            ? null
                                            : book.getAuthor().getName(),
                                    book.getCategory() == null
                                            ? null
                                            : book.getCategory().getName(),
                                    language,
                                    level,
                                    readable,
                                    format,
                                    String.join(", ", new LinkedHashSet<>(reasons))
                            ),
                            score,
                            readable,
                            priority(distance, readable)
                    )
            );
        }

        return candidates.stream()
                .sorted(
                        /*
                         * A fixed priority ladder, then relevance.
                         *
                         * A reader asking for an easy book must not be handed
                         * an unreadable beginner volume while a readable one at
                         * a neighbouring level sits below it, and must never be
                         * handed an advanced book first. Score alone could not
                         * express that: a level mismatch and a missing file are
                         * different kinds of unsuitability.
                         */
                        Comparator.comparingInt(Scored::priority)
                                .thenComparing(
                                        Comparator.comparingDouble(Scored::score)
                                                .reversed()
                                )
                                .thenComparing(
                                        entry -> entry.recommendation().title(),
                                        Comparator.nullsLast(String::compareTo)
                                )
                )
                .limit(wanted)
                .map(Scored::recommendation)
                .toList();
    }

    private record Scored(
            BookRecommendation recommendation,
            double score,
            boolean readable,
            int priority) {
    }

    /**
     * Ordering ladder for a level-specific request:
     *
     *   0  readable, requested level
     *   1  readable, neighbouring level
     *   2  unreadable, requested level (a catalogue alternative)
     *   3  readable, far level (advanced for a beginner, always explained)
     *   4  unreadable, neighbouring level
     *   5  unreadable, far level
     *   6  level unknown on either side
     */
    private int priority(int distance, boolean readable) {

        if (distance < 0) {
            return 6;
        }

        if (readable) {
            return switch (distance) {
                case 0 -> 0;
                case 1 -> 1;
                default -> 3;
            };
        }

        return switch (distance) {
            case 0 -> 2;
            case 1 -> 4;
            default -> 5;
        };
    }

    /** 0 = same level, 1 = neighbouring, 2 = far, -1 = unknown. */
    private int levelDistance(String requested, String actual) {

        if (requested == null || actual == null) {
            return -1;
        }

        int want = rank(requested);
        int have = rank(actual);

        if (want < 0 || have < 0) {
            return -1;
        }

        return Math.min(2, Math.abs(want - have));
    }

    private int rank(String level) {

        String value = level.toUpperCase(Locale.ROOT);

        return switch (value) {
            case "BEGINNER", "ACCESSIBLE", "EASY" -> 0;
            case "INTERMEDIATE", "MEDIUM" -> 1;
            case "ADVANCED", "EXPERT", "HARD" -> 2;
            default -> -1;
        };
    }

    private String levelNote(
            String requested,
            String actual,
            boolean readable,
            String language,
            boolean readableAtRequestedLevel) {

        if (requested == null || actual == null) {
            return null;
        }

        if (requested.equalsIgnoreCase(actual)) {
            return null;
        }

        int distance = levelDistance(requested, actual);

        String wanted = requested.toLowerCase(Locale.ROOT);
        String actualLabel = actual.toLowerCase(Locale.ROOT);

        if (!readable) {
            return actualLabel + " level, catalogued but not available to read";
        }

        if (distance == 1) {

            String alternative =
                    "readable " + actualLabel + " alternative";

            if (readableAtRequestedLevel) {
                return alternative;
            }

            return alternative + "; no readable "
                    + wanted
                    + (language == null ? " title" : " " + language + " title")
                    + " exists in the collection";
        }

        boolean below = levelDistance(requested, actual) == 2
                && rank(actual) < rank(requested);

        return actualLabel + " level \u2014 "
                + (below ? "below" : "above")
                + " the " + wanted + " level you asked for";
    }

    /**
     * First books for a reader who has not chosen a language yet.
     *
     * Only readable beginner titles are offered, because recommending a
     * book that cannot be opened would be a dead end for exactly the reader
     * who needs a place to start.
     */
    public List<BookRecommendation> starters(int limit) {

        int wanted = Math.min(Math.max(limit, 1), MAX_RECOMMENDATIONS);

        Map<Long, List<PathwayBook>> placements = new HashMap<>();

        for (PathwayBook membership : pathwayBookRepository.findAll()) {
            if (membership.getBook() == null) {
                continue;
            }
            placements.computeIfAbsent(
                    membership.getBook().getId(),
                    key -> new ArrayList<>()
            ).add(membership);
        }

        return placements.entrySet().stream()
                .filter(entry -> "BEGINNER".equalsIgnoreCase(levelOf(entry.getValue())))
                .map(entry -> {

                    Book book = bookRepository.findById(entry.getKey()).orElse(null);

                    if (book == null) {
                        return null;
                    }

                    boolean readable = isOpenable(book);

                    if (!readable || bookLanguage(book) == null) {
                        return null;
                    }

                    return new BookRecommendation(
                            book.getId(),
                            book.getTitle(),
                            book.getAuthor() == null ? null : book.getAuthor().getName(),
                            book.getCategory() == null ? null : book.getCategory().getName(),
                            bookLanguage(book),
                            "BEGINNER",
                            true,
                            formatOf(book),
                            "a beginner book you can open now"
                    );
                })
                .filter(java.util.Objects::nonNull)
                .sorted(
                        Comparator.comparing(
                                BookRecommendation::language,
                                Comparator.nullsLast(String::compareTo)
                        ).thenComparing(
                                BookRecommendation::title,
                                Comparator.nullsLast(String::compareTo)
                        )
                )
                .limit(wanted)
                .toList();
    }

    // =========================================================
    // MATCHING
    // =========================================================

    private String bookLanguage(Book book) {

        for (String genre : safe(book.getGenres())) {
            String canonical = canonicalLanguage(genre);
            if (canonical != null) {
                return canonical;
            }
        }

        for (String subject : safe(book.getSubjects())) {
            String canonical = canonicalLanguage(subject);
            if (canonical != null) {
                return canonical;
            }
        }

        return null;
    }

    /**
     * Reads a language out of a genre or subject value.
     *
     * Catalogued subjects are phrases, not bare names: the open textbooks
     * carry "Python (Computer program language)" and "Java (Computer program
     * language)". Matching only the exact string "python" silently dropped
     * every one of them from a language-filtered recommendation, so "find an
     * easy python book" answered with a metadata-only title while readable
     * beginner Python books sat in the collection unmentioned.
     *
     * Tokens are compared whole, which is what keeps "Computer Science" from
     * reading as C and "JavaScript" from reading as Java.
     */
    private String canonicalLanguage(String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        String lower = value.toLowerCase(Locale.ROOT);

        for (String token : lower.split("[^a-z0-9+#]+")) {

            String language = switch (token) {
                case "java" -> "Java";
                case "javascript", "js" -> "JavaScript";
                case "python" -> "Python";
                case "c++", "cpp" -> "C++";
                case "c#", "csharp" -> "C#";
                case "rust" -> "Rust";
                case "go", "golang" -> "Go";
                case "c" -> "C";
                default -> null;
            };

            if (language != null) {
                return language;
            }
        }

        return null;
    }

    private boolean matchesAnyLanguage(Book book, Set<String> languages) {

        for (String genre : safe(book.getGenres())) {
            String canonical = canonicalLanguage(genre);
            if (canonical != null && languages.contains(canonical)) {
                return true;
            }
        }

        return false;
    }

    /**
     * "programming book" excludes novels: a title's level is irrelevant when
     * the subject is wrong.
     */
    private boolean isProgrammingBook(Book book) {

        List<String> haystack = new ArrayList<>();
        haystack.addAll(safe(book.getSubjects()));
        haystack.addAll(safe(book.getGenres()));

        if (book.getCategory() != null && book.getCategory().getName() != null) {
            haystack.add(book.getCategory().getName());
        }

        if (book.getTitle() != null) {
            haystack.add(book.getTitle());
        }

        for (String value : haystack) {

            String lower = value.toLowerCase(Locale.ROOT);

            if (lower.contains("programming")
                    || lower.contains("coding")
                    || lower.contains("software")
                    || lower.contains("computer science")) {
                return true;
            }
        }

        return false;
    }

    private boolean bookMatchesTopic(Book book, String topic) {

        List<String> haystack = new ArrayList<>();
        haystack.addAll(safe(book.getSubjects()));
        haystack.addAll(safe(book.getGenres()));

        if (book.getCategory() != null && book.getCategory().getName() != null) {
            haystack.add(book.getCategory().getName());
        }

        if (book.getTitle() != null) {
            haystack.add(book.getTitle());
        }

        for (String value : haystack) {

            String lower = value.toLowerCase(Locale.ROOT);

            switch (topic) {
                case "Algorithms" -> {
                    if (lower.contains("algorithm")) {
                        return true;
                    }
                }
                case "Data Structures" -> {
                    if (lower.contains("data structure")
                            || lower.contains("linked list")
                            || lower.contains("array")
                            || lower.contains("stack")) {
                        return true;
                    }
                }
                case "Object-Oriented Programming" -> {
                    if (lower.contains("object-oriented")
                            || lower.contains("object oriented")
                            || lower.contains("programming")) {
                        return true;
                    }
                }
                case "Operating Systems" -> {
                    if (lower.contains("operating system") || lower.contains("kernel")) {
                        return true;
                    }
                }
                case "Networking" -> {
                    if (lower.contains("network") || lower.contains("tcp")) {
                        return true;
                    }
                }
                case "Machine Learning" -> {
                    if (lower.contains("machine learning")
                            || lower.contains("neural")
                            || lower.contains("learning")) {
                        return true;
                    }
                }
                case "Databases" -> {
                    if (lower.contains("database") || lower.contains("sql")) {
                        return true;
                    }
                }
                case "Web Development" -> {
                    if (lower.contains("web") || lower.contains("javascript")) {
                        return true;
                    }
                }
                case "Concurrency" -> {
                    if (lower.contains("concurren") || lower.contains("thread")) {
                        return true;
                    }
                }
                default -> {
                    if (lower.contains(topic.toLowerCase(Locale.ROOT))) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private String levelOf(List<PathwayBook> memberships) {

        for (PathwayBook membership : memberships) {
            if (membership.getLevel() != null && !membership.getLevel().isBlank()) {
                return membership.getLevel().toUpperCase(Locale.ROOT);
            }
        }

        return null;
    }

    // =========================================================
    // AVAILABILITY
    // =========================================================

    private Set<String> indexedDocumentIds() {

        Set<String> ids = new HashSet<>();

        for (VectorRecord record : vectorIndexService.records()) {
            if (record.getDocumentId() != null) {
                ids.add(record.getDocumentId());
            }
        }

        return ids;
    }

    /** A stored edition exists, so the book can be opened. */
    private boolean isOpenable(Book book) {

        return book.getResourceId() != null
                && book.getDigitalResources() != null
                && !book.getDigitalResources().isEmpty();
    }

    private boolean isReadable(Book book, Set<String> indexedDocuments) {

        if (book.getResourceId() != null
                && indexedDocuments.contains(book.getResourceId())
                && book.getDigitalResources() != null
                && !book.getDigitalResources().isEmpty()) {
            return true;
        }

        return false;
    }

    private String formatOf(Book book) {

        if (book.getDigitalResources() == null || book.getDigitalResources().isEmpty()) {
            return null;
        }

        DigitalResource resource = book.getDigitalResources().get(0);
        String name = resource.getFileName() == null
                ? ""
                : resource.getFileName().toLowerCase(Locale.ROOT);

        if (name.endsWith(".pdf")) {
            return "PDF";
        }
        if (name.endsWith(".html") || name.endsWith(".htm")) {
            return "HTML";
        }
        if (name.endsWith(".epub")) {
            return "EPUB";
        }

        return resource.getFormat();
    }

    private List<String> safe(List<String> values) {
        return values == null ? List.of() : values;
    }
}
