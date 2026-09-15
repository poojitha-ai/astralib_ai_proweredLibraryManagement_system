package com.astralib.backend;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Service
public class KnowledgeGraphService {

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private VectorRecordRepository vectorRecordRepository;


    // =========================================================
    // TEXT / CONCEPT EXTRACTION CONFIGURATION
    // =========================================================

    private static final Pattern WORD_PATTERN =
            Pattern.compile(
                    "[A-Za-z][A-Za-z'-]{2,}"
            );


    /*
     * Detects named phrases such as:
     *
     * Sherlock Holmes
     * Arthur Conan Doyle
     * Jabez Wilson
     * Red-Headed League
     * Baker Street
     */
    private static final Pattern PROPER_PHRASE_PATTERN =
            Pattern.compile(
                    "\\b(?:[A-Z][A-Za-z'-]{1,})"
                            + "(?:\\s+(?:[A-Z][A-Za-z'-]{1,})){1,3}\\b"
            );


    // =========================================================
    // STOP WORDS
    // =========================================================

    private static final Set<String> STOP_WORDS =
            new HashSet<>(
                    Arrays.asList(

                            "the", "and", "for", "that",
                            "with", "this", "from", "was",
                            "were", "have", "has", "had",

                            "his", "her", "their", "our",
                            "your", "you", "yourself",

                            "into", "upon", "about", "after",
                            "before", "when", "where", "which",

                            "what", "who", "whom", "whose",

                            "would", "could", "should", "might",
                            "must", "will", "shall",

                            "there", "here", "then", "than",
                            "they", "them", "these", "those",

                            "been", "being", "are", "is",
                            "am", "be",

                            "but", "not", "only", "very",
                            "much", "more", "most", "some",
                            "such", "any", "all",

                            "one", "two", "three",
                            "first", "second",

                            "said", "says", "say",
                            "asked", "answered",
                            "replied", "cried",

                            "him", "she", "he", "its",
                            "it", "we", "us",

                            "did", "does", "do",
                            "done",

                            "can", "may",

                            "out", "over", "under",
                            "again", "against",

                            "also", "just", "even",

                            "through", "during",

                            "because", "while",

                            "come", "came", "went",
                            "going",

                            "look", "looked",
                            "see", "seen",

                            "made", "make",

                            "know", "knew",

                            "think", "thought",

                            "little", "great",

                            "well", "quite",

                            "however",

                            "something", "anything",
                            "nothing",

                            "chapter", "section",

                            "adventure", "adventures",

                            // Generic time words
                            "time", "times",
                            "day", "days",
                            "week", "weeks",
                            "year", "years",
                            "moment", "moments",

                            // Generic words
                            "thing", "things",
                            "way", "ways",
                            "place", "places",
                            "part", "parts",
                            "case", "cases",
                            "point", "points",

                            // Directional / positional noise
                            "down",
                            "up",
                            "left",
                            "right",
                            "away",
                            "back",
                            "forward",
                            "inside",
                            "outside"
                    )
            );


    // =========================================================
    // PHASE 1
    // BOOK METADATA KNOWLEDGE GRAPH
    // =========================================================

    public GraphResponse getBookGraph(
            Long bookId
    ) {

        Book book =
                getBook(
                        bookId
                );


        String documentId =
                getBookDocumentId(
                        book
                );


        List<VectorRecord> records =
                getDocumentRecords(
                        documentId
                );


        Map<String, GraphNode> nodeMap =
                new LinkedHashMap<>();


        List<GraphEdge> edges =
                new ArrayList<>();


        Set<String> createdEdges =
                new HashSet<>();


        // =====================================================
        // BOOK NODE
        // =====================================================

        String bookNodeId =
                "book:"
                        + book.getId();


        nodeMap.put(
                bookNodeId,

                new GraphNode(
                        bookNodeId,
                        book.getTitle(),
                        "BOOK",
                        book.getId(),
                        documentId
                )
        );


        // =====================================================
        // METADATA NODES
        // =====================================================

        for (
                VectorRecord record :
                records
        ) {

            if (
                    record == null
            ) {
                continue;
            }


            // -------------------------------------------------
            // AUTHOR
            // -------------------------------------------------

            String author =
                    clean(
                            record.getAuthor()
                    );


            if (
                    author != null
            ) {

                String authorNodeId =
                        buildNodeId(
                                "author",
                                author
                        );


                nodeMap.putIfAbsent(
                        authorNodeId,

                        new GraphNode(
                                authorNodeId,
                                author,
                                "AUTHOR",
                                null,
                                null
                        )
                );


                addEdge(
                        edges,
                        createdEdges,
                        bookNodeId,
                        authorNodeId,
                        "WRITTEN_BY"
                );
            }


            // -------------------------------------------------
            // CATEGORY
            // -------------------------------------------------

            String category =
                    clean(
                            record.getCategory()
                    );


            if (
                    category != null
            ) {

                String categoryNodeId =
                        buildNodeId(
                                "category",
                                category
                        );


                nodeMap.putIfAbsent(
                        categoryNodeId,

                        new GraphNode(
                                categoryNodeId,
                                category,
                                "CATEGORY",
                                null,
                                null
                        )
                );


                addEdge(
                        edges,
                        createdEdges,
                        bookNodeId,
                        categoryNodeId,
                        "IN_CATEGORY"
                );
            }


            // -------------------------------------------------
            // SECTION
            // -------------------------------------------------

            String section =
                    clean(
                            record.getSection()
                    );


            if (
                    section != null
            ) {

                String sectionNodeId =
                        buildNodeId(
                                "section",
                                documentId
                                        + ":"
                                        + section
                        );


                nodeMap.putIfAbsent(
                        sectionNodeId,

                        new GraphNode(
                                sectionNodeId,
                                section,
                                "SECTION",
                                book.getId(),
                                documentId
                        )
                );


                addEdge(
                        edges,
                        createdEdges,
                        bookNodeId,
                        sectionNodeId,
                        "HAS_SECTION"
                );
            }
        }


        return new GraphResponse(
                book.getId(),
                documentId,
                book.getTitle(),
                nodeMap
                        .values()
                        .stream()
                        .toList(),
                edges
        );
    }


    // =========================================================
    // PHASE 2
    // CONCEPT GRAPH FOR ONE SECTION
    // =========================================================

    public GraphResponse getSectionConceptGraph(
            Long bookId,
            String requestedSection,
            int conceptLimit
    ) {

        if (
                requestedSection == null
                        ||
                requestedSection.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "Section is required."
            );
        }


        // Minimum 5 concepts.
        // Maximum 20 concepts.
        conceptLimit =
                Math.max(
                        5,
                        Math.min(
                                conceptLimit,
                                20
                        )
                );


        Book book =
                getBook(
                        bookId
                );


        String documentId =
                getBookDocumentId(
                        book
                );


        List<VectorRecord> allRecords =
                getDocumentRecords(
                        documentId
                );


        // =====================================================
        // FIND CHUNKS FOR THE REQUESTED SECTION
        // =====================================================

        List<VectorRecord> sectionRecords =
                new ArrayList<>();


        // -----------------------------------------------------
        // First try exact section name
        // -----------------------------------------------------

        for (
                VectorRecord record :
                allRecords
        ) {

            if (
                    record == null
                            ||
                    record.getSection() == null
            ) {
                continue;
            }


            if (
                    record.getSection()
                            .trim()
                            .equalsIgnoreCase(
                                    requestedSection.trim()
                            )
            ) {

                sectionRecords.add(
                        record
                );
            }
        }


        // -----------------------------------------------------
        // Fallback for partial section names
        //
        // Example:
        //
        // RED-HEADED LEAGUE
        //
        // can match:
        //
        // II. THE RED-HEADED LEAGUE
        // -----------------------------------------------------

        if (
                sectionRecords.isEmpty()
        ) {

            String requestedNormalized =
                    normalizeForSearch(
                            requestedSection
                    );


            for (
                    VectorRecord record :
                    allRecords
            ) {

                if (
                        record == null
                                ||
                        record.getSection() == null
                ) {
                    continue;
                }


                String recordSection =
                        normalizeForSearch(
                                record.getSection()
                        );


                if (
                        recordSection.contains(
                                requestedNormalized
                        )
                                ||
                        requestedNormalized.contains(
                                recordSection
                        )
                ) {

                    sectionRecords.add(
                            record
                    );
                }
            }
        }


        if (
                sectionRecords.isEmpty()
        ) {

            throw new IllegalArgumentException(
                    "Section not found: "
                            + requestedSection
            );
        }


        String resolvedSection =
                sectionRecords
                        .get(0)
                        .getSection()
                        .trim();


        // =====================================================
        // EXTRACT CLEAN CONCEPTS
        // =====================================================

        List<ConceptCandidate> concepts =
                extractConcepts(
                        sectionRecords,
                        book.getTitle(),
                        resolvedSection,
                        conceptLimit
                );


        // =====================================================
        // BUILD GRAPH
        // =====================================================

        Map<String, GraphNode> nodeMap =
                new LinkedHashMap<>();


        List<GraphEdge> edges =
                new ArrayList<>();


        Set<String> createdEdges =
                new HashSet<>();


        // -----------------------------------------------------
        // BOOK NODE
        // -----------------------------------------------------

        String bookNodeId =
                "book:"
                        + book.getId();


        nodeMap.put(
                bookNodeId,

                new GraphNode(
                        bookNodeId,
                        book.getTitle(),
                        "BOOK",
                        book.getId(),
                        documentId
                )
        );


        // -----------------------------------------------------
        // SECTION NODE
        // -----------------------------------------------------

        String sectionNodeId =
                buildNodeId(
                        "section",
                        documentId
                                + ":"
                                + resolvedSection
                );


        nodeMap.put(
                sectionNodeId,

                new GraphNode(
                        sectionNodeId,
                        resolvedSection,
                        "SECTION",
                        book.getId(),
                        documentId
                )
        );


        addEdge(
                edges,
                createdEdges,
                bookNodeId,
                sectionNodeId,
                "HAS_SECTION"
        );


        // -----------------------------------------------------
        // CONCEPT NODES
        // -----------------------------------------------------

        for (
                ConceptCandidate concept :
                concepts
        ) {

            String conceptNodeId =
                    buildNodeId(
                            "concept",
                            concept.normalized
                    );


            nodeMap.putIfAbsent(
                    conceptNodeId,

                    new GraphNode(
                            conceptNodeId,
                            concept.label,
                            "CONCEPT",
                            book.getId(),
                            documentId
                    )
            );


            addEdge(
                    edges,
                    createdEdges,
                    sectionNodeId,
                    conceptNodeId,
                    "HAS_CONCEPT"
            );
        }


        // =====================================================
        // CONCEPT → CONCEPT RELATIONSHIPS
        // =====================================================

        addConceptRelationships(
                concepts,
                sectionRecords,
                edges,
                createdEdges
        );


        return new GraphResponse(
                book.getId(),
                documentId,
                resolvedSection,
                nodeMap
                        .values()
                        .stream()
                        .toList(),
                edges
        );
    }


    // =========================================================
    // CONCEPT EXTRACTION
    // =========================================================

    private List<ConceptCandidate> extractConcepts(
            List<VectorRecord> sectionRecords,
            String bookTitle,
            String sectionTitle,
            int limit
    ) {

        Map<String, ConceptCandidate> candidates =
                new HashMap<>();


        String normalizedBookTitle =
                normalizeForSearch(
                        bookTitle
                );


        String normalizedSectionTitle =
                normalizeForSearch(
                        removeSectionNumber(
                                sectionTitle
                        )
                );


        // =====================================================
        // PROCESS EVERY CHUNK IN THE SECTION
        // =====================================================

        for (
                VectorRecord record :
                sectionRecords
        ) {

            String content =
                    clean(
                            record.getContent()
                    );


            if (
                    content == null
            ) {
                continue;
            }


            // =================================================
            // 1. PROPER PHRASES / NAMED ENTITIES
            // =================================================

            Matcher phraseMatcher =
                    PROPER_PHRASE_PATTERN
                            .matcher(
                                    content.replace(
                                            '\n',
                                            ' '
                                    )
                            );


            while (
                    phraseMatcher.find()
            ) {

                String phrase =
                        cleanCandidateLabel(
                                phraseMatcher.group()
                        );


                if (
                        !isValidConcept(
                                phrase,
                                normalizedBookTitle,
                                normalizedSectionTitle
                        )
                ) {
                    continue;
                }


                String normalized =
                        normalizeForSearch(
                                phrase
                        );


                /*
                 * Proper-name phrases receive
                 * a strong score.
                 */
                addCandidateScore(
                        candidates,
                        normalized,
                        phrase,
                        6.0
                );
            }


            // =================================================
            // 2. NORMAL USEFUL WORDS
            // =================================================

            List<String> words =
                    extractUsefulWords(
                            content
                    );


            for (
                    String word :
                    words
            ) {

                /*
                 * These words help construct phrase
                 * frequency and ranking.
                 *
                 * Single words are later excluded
                 * from Phase 2A output.
                 */
                addCandidateScore(
                        candidates,
                        word,
                        capitalizeWords(
                                word
                        ),
                        1.0
                );
            }


            // =================================================
            // 3. TWO-WORD PHRASES
            // =================================================

            for (
                    int i = 0;
                    i < words.size() - 1;
                    i++
            ) {

                String first =
                        words.get(i);


                String second =
                        words.get(
                                i + 1
                        );


                if (
                        first.equals(
                                second
                        )
                ) {
                    continue;
                }


                String phrase =
                        first
                                + " "
                                + second;


                addCandidateScore(
                        candidates,
                        phrase,
                        capitalizeWords(
                                phrase
                        ),
                        2.5
                );
            }
        }


        // =====================================================
        // RANK CANDIDATES
        // =====================================================

        List<ConceptCandidate> ranked =
                candidates
                        .values()
                        .stream()

                        /*
                         * PHASE 2A:
                         *
                         * Keep meaningful multi-word
                         * entities and phrases.
                         *
                         * This removes generic single
                         * words such as:
                         *
                         * Business
                         * Time
                         * Down
                         *
                         * Abstract single-word concepts
                         * can be handled separately later.
                         */
                        .filter(
                                candidate ->
                                        wordCount(
                                                candidate.normalized
                                        ) >= 2
                                                &&
                                        candidate.score >= 3.0
                        )

                        .sorted(
                                (a, b) -> {

                                    int scoreCompare =
                                            Double.compare(
                                                    b.score,
                                                    a.score
                                            );


                                    if (
                                            scoreCompare != 0
                                    ) {
                                        return scoreCompare;
                                    }


                                    /*
                                     * If scores are equal,
                                     * prefer more specific
                                     * multi-word phrases.
                                     */
                                    return Integer.compare(
                                            wordCount(
                                                    b.normalized
                                            ),
                                            wordCount(
                                                    a.normalized
                                            )
                                    );
                                }
                        )
                        .toList();


        // =====================================================
        // REMOVE DUPLICATE / REDUNDANT CONCEPTS
        // =====================================================

        List<ConceptCandidate> selected =
                new ArrayList<>();


        for (
                ConceptCandidate candidate :
                ranked
        ) {

            boolean redundant =
                    false;


            ConceptCandidate lessSpecific =
                    null;


            for (
                    ConceptCandidate existing :
                    selected
            ) {

                String existingText =
                        " "
                                + existing.normalized
                                + " ";


                String candidateText =
                        " "
                                + candidate.normalized
                                + " ";


                // -------------------------------------------------
                // EXISTING CONCEPT IS MORE SPECIFIC
                //
                // Existing:
                // Saxe-Coburg Square
                //
                // Candidate:
                // Coburg Square
                //
                // Skip candidate.
                // -------------------------------------------------

                if (
                        existingText.contains(
                                candidateText
                        )
                ) {

                    redundant =
                            true;

                    break;
                }


                // -------------------------------------------------
                // CANDIDATE IS MORE SPECIFIC
                //
                // Existing:
                // Coburg Square
                //
                // Candidate:
                // Saxe-Coburg Square
                //
                // Replace existing.
                // -------------------------------------------------

                if (
                        candidateText.contains(
                                existingText
                        )
                                &&
                        candidate.normalized.length()
                                >
                        existing.normalized.length()
                ) {

                    lessSpecific =
                            existing;

                    break;
                }
            }


            if (
                    redundant
            ) {
                continue;
            }


            if (
                    lessSpecific != null
            ) {

                selected.remove(
                        lessSpecific
                );
            }


            selected.add(
                    candidate
            );


            if (
                    selected.size()
                            >= limit
            ) {

                break;
            }
        }


        // =====================================================
        // FINAL PARTIAL-PHRASE CLEANUP
        // =====================================================

        /*
         * This catches cases that survive because of
         * ranking order.
         *
         * Example:
         *
         * Red-headed
         * Red-headed League
         *
         * Final result:
         *
         * Red-headed League
         */

        List<ConceptCandidate> finalSelected =
                new ArrayList<>();


        for (
                ConceptCandidate candidate :
                selected
        ) {

            boolean containedInBetterConcept =
                    false;


            for (
                    ConceptCandidate other :
                    selected
            ) {

                if (
                        candidate == other
                ) {
                    continue;
                }


                String candidateText =
                        " "
                                + candidate.normalized
                                + " ";


                String otherText =
                        " "
                                + other.normalized
                                + " ";


                if (
                        other.normalized.length()
                                >
                        candidate.normalized.length()
                                &&
                        otherText.contains(
                                candidateText
                        )
                ) {

                    containedInBetterConcept =
                            true;

                    break;
                }
            }


            if (
                    !containedInBetterConcept
            ) {

                finalSelected.add(
                        candidate
                );
            }
        }


        return finalSelected;
    }


    // =========================================================
    // CONCEPT RELATIONSHIPS
    // USING CHUNK CO-OCCURRENCE
    // =========================================================

    private void addConceptRelationships(
            List<ConceptCandidate> concepts,
            List<VectorRecord> sectionRecords,
            List<GraphEdge> edges,
            Set<String> createdEdges
    ) {

        if (
                concepts.size() < 2
        ) {
            return;
        }


        Map<String, RelatedPair> pairMap =
                new HashMap<>();


        // =====================================================
        // CHECK EACH CHUNK
        // =====================================================

        for (
                VectorRecord record :
                sectionRecords
        ) {

            String content =
                    normalizeForSearch(
                            record.getContent()
                    );


            if (
                    content.isBlank()
            ) {
                continue;
            }


            List<ConceptCandidate> present =
                    new ArrayList<>();


            String paddedContent =
                    " "
                            + content
                            + " ";


            // -------------------------------------------------
            // Find concepts occurring in this chunk
            // -------------------------------------------------

            for (
                    ConceptCandidate concept :
                    concepts
            ) {

                String search =
                        " "
                                + concept.normalized
                                + " ";


                if (
                        paddedContent.contains(
                                search
                        )
                ) {

                    present.add(
                            concept
                    );
                }
            }


            // -------------------------------------------------
            // Count pair co-occurrences
            // -------------------------------------------------

            for (
                    int i = 0;
                    i < present.size();
                    i++
            ) {

                for (
                        int j = i + 1;
                        j < present.size();
                        j++
                ) {

                    ConceptCandidate a =
                            present.get(i);


                    ConceptCandidate b =
                            present.get(j);


                    String first =
                            a.normalized.compareTo(
                                    b.normalized
                            ) <= 0
                                    ? a.normalized
                                    : b.normalized;


                    String second =
                            first.equals(
                                    a.normalized
                            )
                                    ? b.normalized
                                    : a.normalized;


                    String key =
                            first
                                    + "|"
                                    + second;


                    RelatedPair pair =
                            pairMap.computeIfAbsent(
                                    key,

                                    ignored ->
                                            new RelatedPair(
                                                    first,
                                                    second
                                            )
                            );


                    pair.count++;
                }
            }
        }


        // =====================================================
        // RANK RELATIONSHIPS
        // =====================================================

        List<RelatedPair> rankedPairs =
                pairMap
                        .values()
                        .stream()
                        .sorted(
                                (a, b) ->
                                        Integer.compare(
                                                b.count,
                                                a.count
                                        )
                        )
                        .toList();


        /*
         * Prefer concept pairs that appear
         * together in at least two chunks.
         */
        List<RelatedPair> strongPairs =
                rankedPairs
                        .stream()
                        .filter(
                                pair ->
                                        pair.count >= 2
                        )
                        .toList();


        List<RelatedPair> pairsToUse;


        if (
                !strongPairs.isEmpty()
        ) {

            pairsToUse =
                    strongPairs;

        } else {

            /*
             * Small-section fallback.
             *
             * If no relationship appears
             * in multiple chunks, use the
             * strongest one-chunk pairs.
             */
            pairsToUse =
                    rankedPairs;
        }


        int maxRelations =
                Math.min(
                        18,
                        pairsToUse.size()
                );


        // =====================================================
        // CREATE RELATED_TO EDGES
        // =====================================================

        for (
                int i = 0;
                i < maxRelations;
                i++
        ) {

            RelatedPair pair =
                    pairsToUse.get(i);


            String sourceNode =
                    buildNodeId(
                            "concept",
                            pair.first
                    );


            String targetNode =
                    buildNodeId(
                            "concept",
                            pair.second
                    );


            addEdge(
                    edges,
                    createdEdges,
                    sourceNode,
                    targetNode,
                    "RELATED_TO"
            );
        }
    }


    // =========================================================
    // CANDIDATE SCORE
    // =========================================================

    private void addCandidateScore(
            Map<String, ConceptCandidate> candidates,
            String normalized,
            String label,
            double score
    ) {

        if (
                normalized == null
                        ||
                normalized.isBlank()
        ) {
            return;
        }


        ConceptCandidate candidate =
                candidates.get(
                        normalized
                );


        if (
                candidate == null
        ) {

            candidates.put(
                    normalized,

                    new ConceptCandidate(
                            normalized,
                            label,
                            score
                    )
            );

        } else {

            candidate.score +=
                    score;


            /*
             * Prefer the nicer label when a
             * capitalized entity version
             * appears later.
             */
            if (
                    label != null
                            &&
                    containsUpperCase(
                            label
                    )
            ) {

                candidate.label =
                        label;
            }
        }
    }


    // =========================================================
    // EXTRACT USEFUL WORDS
    // =========================================================

    private List<String> extractUsefulWords(
            String text
    ) {

        List<String> result =
                new ArrayList<>();


        if (
                text == null
        ) {
            return result;
        }


        Matcher matcher =
                WORD_PATTERN.matcher(
                        text
                );


        while (
                matcher.find()
        ) {

            String word =
                    matcher.group()
                            .toLowerCase(
                                    Locale.ROOT
                            )
                            .replace(
                                    "'",
                                    ""
                            );


            if (
                    word.length() < 4
                            ||
                    STOP_WORDS.contains(
                            word
                    )
            ) {
                continue;
            }


            result.add(
                    word
            );
        }


        return result;
    }


    // =========================================================
    // VALIDATE CONCEPT
    // =========================================================

    private boolean isValidConcept(
            String phrase,
            String normalizedBookTitle,
            String normalizedSectionTitle
    ) {

        if (
                phrase == null
                        ||
                phrase.isBlank()
        ) {
            return false;
        }


        String normalized =
                normalizeForSearch(
                        phrase
                );


        if (
                normalized.isBlank()
                        ||
                normalized.length() < 4
        ) {
            return false;
        }


        // -----------------------------------------------------
        // Don't create the whole book title as a concept
        // -----------------------------------------------------

        if (
                normalized.equals(
                        normalizedBookTitle
                )
        ) {
            return false;
        }


        // -----------------------------------------------------
        // Don't create the whole section title as a concept
        // -----------------------------------------------------

        if (
                normalized.equals(
                        normalizedSectionTitle
                )
        ) {
            return false;
        }


        // -----------------------------------------------------
        // Avoid very long phrase nodes
        // -----------------------------------------------------

        if (
                wordCount(
                        normalized
                ) > 4
        ) {
            return false;
        }


        return true;
    }


    // =========================================================
    // BOOK HELPER
    // =========================================================

    private Book getBook(
            Long bookId
    ) {

        return bookRepository
                .findById(
                        bookId
                )
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "Book not found: "
                                                + bookId
                                )
                );
    }


    // =========================================================
    // BOOK ID → VECTOR DOCUMENT ID
    // =========================================================

    private String getBookDocumentId(
            Book book
    ) {

        if (
                book.getResourceId() != null
                        &&
                !book.getResourceId()
                        .isBlank()
        ) {

            return book
                    .getResourceId()
                    .trim();
        }


        return "BOOK-ID-"
                + book.getId();
    }


    // =========================================================
    // LOAD VECTOR RECORDS
    // =========================================================

    private List<VectorRecord> getDocumentRecords(
            String documentId
    ) {

        List<VectorRecord> records =
                vectorRecordRepository
                        .findByDocumentId(
                                documentId
                        );


        if (
                records == null
                        ||
                records.isEmpty()
        ) {

            records =
                    vectorRecordRepository
                            .findByDocumentIdContainingIgnoreCase(
                                    documentId
                            );
        }


        if (
                records == null
        ) {

            return Collections.emptyList();
        }


        return records;
    }


    // =========================================================
    // ADD EDGE WITHOUT DUPLICATES
    // =========================================================

    private void addEdge(
            List<GraphEdge> edges,
            Set<String> createdEdges,
            String source,
            String target,
            String relation
    ) {

        String edgeId =
                source
                        + "|"
                        + relation
                        + "|"
                        + target;


        if (
                createdEdges.add(
                        edgeId
                )
        ) {

            edges.add(
                    new GraphEdge(
                            edgeId,
                            source,
                            target,
                            relation
                    )
            );
        }
    }


    // =========================================================
    // NODE ID
    // =========================================================

    private String buildNodeId(
            String type,
            String value
    ) {

        String normalized =
                normalizeForSearch(
                        value
                );


        return type
                + ":"
                + Integer.toHexString(
                        normalized.hashCode()
                );
    }


    // =========================================================
    // BASIC TEXT CLEANING
    // =========================================================

    private String clean(
            String value
    ) {

        if (
                value == null
        ) {
            return null;
        }


        String cleaned =
                value.trim();


        if (
                cleaned.isEmpty()
                        ||
                cleaned.equalsIgnoreCase(
                        "unknown"
                )
        ) {

            return null;
        }


        return cleaned;
    }


    // =========================================================
    // CLEAN CONCEPT LABEL
    // =========================================================

    private String cleanCandidateLabel(
            String value
    ) {

        if (
                value == null
        ) {
            return null;
        }


        return value
                .replaceAll(
                        "\\s+",
                        " "
                )
                .trim();
    }


    // =========================================================
    // NORMALIZE TEXT FOR MATCHING
    // =========================================================

    private String normalizeForSearch(
            String value
    ) {

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
                .replaceAll(
                        "\\s+",
                        " "
                )
                .trim();
    }


    // =========================================================
    // REMOVE ROMAN-NUMERAL SECTION PREFIX
    //
    // Example:
    //
    // II. THE RED-HEADED LEAGUE
    //
    // becomes:
    //
    // THE RED-HEADED LEAGUE
    // =========================================================

    private String removeSectionNumber(
            String value
    ) {

        if (
                value == null
        ) {
            return "";
        }


        return value
                .replaceFirst(
                        "(?i)^\\s*[ivxlcdm]+\\.?\\s*",
                        ""
                )
                .trim();
    }


    // =========================================================
    // CAPITALIZE PHRASE
    // =========================================================

    private String capitalizeWords(
            String value
    ) {

        if (
                value == null
                        ||
                value.isBlank()
        ) {
            return value;
        }


        String[] parts =
                value.split(
                        "\\s+"
                );


        StringBuilder result =
                new StringBuilder();


        for (
                String part :
                parts
        ) {

            if (
                    part.isBlank()
            ) {
                continue;
            }


            if (
                    result.length() > 0
            ) {

                result.append(
                        ' '
                );
            }


            result.append(
                    Character.toUpperCase(
                            part.charAt(0)
                    )
            );


            if (
                    part.length() > 1
            ) {

                result.append(
                        part.substring(1)
                );
            }
        }


        return result.toString();
    }


    // =========================================================
    // CHECK CAPITALIZATION
    // =========================================================

    private boolean containsUpperCase(
            String value
    ) {

        if (
                value == null
        ) {
            return false;
        }


        for (
                char c :
                value.toCharArray()
        ) {

            if (
                    Character.isUpperCase(
                            c
                    )
            ) {

                return true;
            }
        }


        return false;
    }


    // =========================================================
    // WORD COUNT
    // =========================================================

    private int wordCount(
            String value
    ) {

        if (
                value == null
                        ||
                value.isBlank()
        ) {

            return 0;
        }


        return value
                .trim()
                .split(
                        "\\s+"
                )
                .length;
    }


    // =========================================================
    // INTERNAL CONCEPT OBJECT
    // =========================================================

    private static class ConceptCandidate {

        private final String normalized;

        private String label;

        private double score;


        private ConceptCandidate(
                String normalized,
                String label,
                double score
        ) {

            this.normalized =
                    normalized;

            this.label =
                    label;

            this.score =
                    score;
        }
    }


    // =========================================================
    // INTERNAL RELATIONSHIP OBJECT
    // =========================================================

    private static class RelatedPair {

        private final String first;

        private final String second;

        private int count;


        private RelatedPair(
                String first,
                String second
        ) {

            this.first =
                    first;

            this.second =
                    second;

            this.count =
                    0;
        }
    }


    // =========================================================
    // GRAPH RESPONSE DTO
    // =========================================================

    public static class GraphResponse {

        private Long bookId;

        private String documentId;

        private String title;

        private List<GraphNode> nodes;

        private List<GraphEdge> edges;


        public GraphResponse(
                Long bookId,
                String documentId,
                String title,
                List<GraphNode> nodes,
                List<GraphEdge> edges
        ) {

            this.bookId =
                    bookId;

            this.documentId =
                    documentId;

            this.title =
                    title;

            this.nodes =
                    nodes;

            this.edges =
                    edges;
        }


        public Long getBookId() {
            return bookId;
        }


        public String getDocumentId() {
            return documentId;
        }


        public String getTitle() {
            return title;
        }


        public List<GraphNode> getNodes() {
            return nodes;
        }


        public List<GraphEdge> getEdges() {
            return edges;
        }
    }


    // =========================================================
    // GRAPH NODE DTO
    // =========================================================

    public static class GraphNode {

        private String id;

        private String label;

        private String type;

        private Long bookId;

        private String documentId;


        public GraphNode(
                String id,
                String label,
                String type,
                Long bookId,
                String documentId
        ) {

            this.id =
                    id;

            this.label =
                    label;

            this.type =
                    type;

            this.bookId =
                    bookId;

            this.documentId =
                    documentId;
        }


        public String getId() {
            return id;
        }


        public String getLabel() {
            return label;
        }


        public String getType() {
            return type;
        }


        public Long getBookId() {
            return bookId;
        }


        public String getDocumentId() {
            return documentId;
        }
    }


    // =========================================================
    // GRAPH EDGE DTO
    // =========================================================

    public static class GraphEdge {

        private String id;

        private String source;

        private String target;

        private String relation;


        public GraphEdge(
                String id,
                String source,
                String target,
                String relation
        ) {

            this.id =
                    id;

            this.source =
                    source;

            this.target =
                    target;

            this.relation =
                    relation;
        }


        public String getId() {
            return id;
        }


        public String getSource() {
            return source;
        }


        public String getTarget() {
            return target;
        }


        public String getRelation() {
            return relation;
        }
    }
}