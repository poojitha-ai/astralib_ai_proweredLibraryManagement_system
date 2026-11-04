package com.astralib.backend;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class BookSummarizationService {

    private static final int BATCH_CHAR_LIMIT = 12000;
    private static final int REDUCTION_GROUP_SIZE = 8;
    private static final int MAX_CITATION_CHUNKS = 12;

    private static final String SUMMARY_SYSTEM_INSTRUCTION =
            "You are an academic book analyst. Use only the supplied book text. "
            + "Do not invent chapters, events, examples, quotations, or claims. "
            + "Preserve the author's meaning and distinguish explicit content "
            + "from reasonable interpretation. Be comprehensive and organized.";

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private VectorIndexService vectorIndexService;

    @Autowired
    private LlmService llmService;

    public record SummaryResult(
            String answer,
            Long bookId,
            List<VectorRecord> sourceChunks,
            boolean grounded
    ) {}

    public boolean isSummaryRequest(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }

        String text = message.toLowerCase(Locale.ROOT).replace("-", " ");

        return containsAny(text,
                "summarize the book",
                "summarise the book",
                "summarize book",
                "summarise book",
                "full book summary",
                "entire book summary",
                "complete book summary",
                "summarize the entire",
                "summarise the entire",
                "summarize the whole",
                "summarise the whole",
                "give me a summary of",
                "give me the summary of",
                "provide a summary of",
                "book overview of",
                "chapter by chapter summary",
                "chapter-wise summary",
                "chapter wise summary"
        );
    }

    public SummaryResult summarizeRequest(String message) {

        BookMatch match = findBookMentionedInMessage(message);

        if (match.ambiguous()) {
            String titles = match.books().stream()
                    .map(Book::getTitle)
                    .filter(title -> title != null && !title.isBlank())
                    .distinct()
                    .collect(Collectors.joining(", "));

            return new SummaryResult(
                    "I found more than one possible book matching your request: "
                            + titles
                            + ". Please mention the exact book title so I summarize the correct one.",
                    null,
                    List.of(),
                    false
            );
        }

        if (match.books().isEmpty()) {
            return new SummaryResult(
                    "I couldn't identify the book title in your request. "
                            + "Please ask again using the exact title shown in the AstraLib catalogue.",
                    null,
                    List.of(),
                    false
            );
        }

        Book book = match.books().get(0);

        List<VectorRecord> chunks = findAllBookChunks(book);

        if (chunks.isEmpty()) {
            return new SummaryResult(
                    "I found \"" + book.getTitle()
                            + "\" in the catalogue, but it has no indexed readable book content yet. "
                            + "A full-book summary is not available for this title.",
                    book.getId(),
                    List.of(),
                    false
            );
        }

        if (!llmService.isConfigured()) {
            return new SummaryResult(
                    "The book content is indexed, but the summary model is not configured. "
                            + "Please check the Gemini LLM configuration.",
                    book.getId(),
                    List.of(),
                    false
            );
        }

        try {
            String summary = createSummary(book, chunks);

            if (summary == null || summary.isBlank()) {
                throw new IllegalStateException("The model returned an empty summary.");
            }

            return new SummaryResult(
                    summary,
                    book.getId(),
                    selectCitationChunks(chunks),
                    true
            );

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Could not generate the full-book summary: " + e.getMessage(),
                    e
            );
        }
    }

    private BookMatch findBookMentionedInMessage(String message) {

        if (message == null || message.isBlank()) {
            return new BookMatch(List.of(), false);
        }

        String normalizedMessage = normalize(message);

        List<Book> matches = bookRepository.findAll().stream()
                .filter(book -> book.getTitle() != null
                        && !book.getTitle().isBlank())
                .filter(book -> {
                    String title = normalize(book.getTitle());
                    return title.length() >= 3
                            && normalizedMessage.contains(title);
                })
                .sorted(Comparator.comparingInt(
                        (Book book) -> normalize(book.getTitle()).length()
                ).reversed())
                .collect(Collectors.toList());

        if (matches.isEmpty()) {
            return new BookMatch(List.of(), false);
        }

        int longestLength = normalize(matches.get(0).getTitle()).length();

        List<Book> longestMatches = matches.stream()
                .filter(book -> normalize(book.getTitle()).length() == longestLength)
                .collect(Collectors.toList());

        if (longestMatches.size() > 1) {
            return new BookMatch(longestMatches, true);
        }

        return new BookMatch(List.of(longestMatches.get(0)), false);
    }

    private List<VectorRecord> findAllBookChunks(Book book) {

        Set<String> documentIds = new LinkedHashSet<>();

        if (book.getResourceId() != null && !book.getResourceId().isBlank()) {
            documentIds.add(book.getResourceId().trim().toLowerCase(Locale.ROOT));
        }

        if (book.getId() != null) {
            documentIds.add(
                    ("BOOK-ID-" + book.getId()).toLowerCase(Locale.ROOT)
            );
        }

        if (documentIds.isEmpty()) {
            return List.of();
        }

        return vectorIndexService.records("book").stream()
                .filter(record -> record != null)
                .filter(record -> record.getDocumentId() != null)
                .filter(record -> documentIds.contains(
                        record.getDocumentId().trim().toLowerCase(Locale.ROOT)
                ))
                .filter(record -> record.getContent() != null
                        && !record.getContent().isBlank())
                .sorted(
                        Comparator.comparing(
                                VectorRecord::getChunkIndex,
                                Comparator.nullsLast(Integer::compareTo)
                        ).thenComparing(
                                record -> record.getChunkId() == null
                                        ? ""
                                        : record.getChunkId()
                        )
                )
                .collect(Collectors.toList());
    }

    private String createSummary(Book book, List<VectorRecord> chunks) {

        List<String> batchSummaries = summarizeBatches(book, chunks);

        while (batchSummaries.size() > REDUCTION_GROUP_SIZE) {

            List<String> reduced = new ArrayList<>();

            for (int i = 0; i < batchSummaries.size();
                 i += REDUCTION_GROUP_SIZE) {

                List<String> group = batchSummaries.subList(
                        i,
                        Math.min(i + REDUCTION_GROUP_SIZE, batchSummaries.size())
                );

                String prompt = """
                        Combine the following consecutive partial summaries
                        of the same book into one accurate, detailed summary.

                        Preserve all important ideas, concepts, examples,
                        arguments, and section progression.
                        Remove repetition, but do not discard unique information.
                        Do not add facts that are not present in these summaries.

                        PARTIAL SUMMARIES:
                        """ + numberedText(group);

                reduced.add(
                        llmService.generateLongSummary(
                                SUMMARY_SYSTEM_INSTRUCTION,
                                prompt,
                                1800
                        )
                );
            }

            batchSummaries = reduced;
        }

        String finalPrompt = """
                Prepare a comprehensive full-book summary using the
                following summaries of consecutive portions of the book.

                BOOK TITLE: %s

                REQUIRED STRUCTURE:
                1. Book overview and central theme
                2. Detailed section or chapter-wise progression
                3. Important concepts, arguments, and explanations
                4. Important examples and applications actually present
                5. Connections between major ideas
                6. Key lessons and takeaways
                7. A concise final recap for a student

                Be detailed and educational. Preserve the original order
                of the material. Do not invent chapter names or events.
                If chapter boundaries are unavailable in the source,
                organize the discussion by the available section headings.

                PARTIAL SUMMARIES:
                %s
                """.formatted(book.getTitle(), numberedText(batchSummaries));

        return llmService.generateLongSummary(
                SUMMARY_SYSTEM_INSTRUCTION,
                finalPrompt,
                5000
        );
    }

    private List<String> summarizeBatches(
            Book book,
            List<VectorRecord> chunks) {

        List<String> summaries = new ArrayList<>();
        StringBuilder batch = new StringBuilder();
        int batchNumber = 1;
        int firstChunkIndex = -1;
        int lastChunkIndex = -1;

        for (VectorRecord chunk : chunks) {

            String content = chunk.getContent().trim();

            String labelledChunk =
                    "\n\n[Chunk " + chunk.getChunkIndex()
                            + " | Section: "
                            + safe(chunk.getSection(), "Unspecified")
                            + "]\n"
                            + content;

            if (batch.length() > 0
                    && batch.length() + labelledChunk.length() > BATCH_CHAR_LIMIT) {

                summaries.add(summarizeOneBatch(
                        book,
                        batchNumber++,
                        firstChunkIndex,
                        lastChunkIndex,
                        batch.toString()
                ));

                batch.setLength(0);
                firstChunkIndex = -1;
            }

            if (firstChunkIndex < 0) {
                firstChunkIndex = chunk.getChunkIndex() == null
                        ? -1
                        : chunk.getChunkIndex();
            }

            lastChunkIndex = chunk.getChunkIndex() == null
                    ? lastChunkIndex
                    : chunk.getChunkIndex();

            batch.append(labelledChunk);
        }

        if (batch.length() > 0) {
            summaries.add(summarizeOneBatch(
                    book,
                    batchNumber,
                    firstChunkIndex,
                    lastChunkIndex,
                    batch.toString()
            ));
        }

        return summaries;
    }

    private String summarizeOneBatch(
            Book book,
            int batchNumber,
            int firstChunk,
            int lastChunk,
            String sourceText) {

        String prompt = """
                Summarize this consecutive portion of the book in detail.

                BOOK: %s
                BATCH: %d
                CHUNK RANGE: %d to %d

                Capture:
                - Every major idea and argument
                - Important definitions and explanations
                - Examples, illustrations, and applications
                - Section-level progression
                - Important names, terms, and distinctions

                Keep the summary faithful to the supplied text.
                Do not introduce outside information.
                Do not write a generic book review.

                SOURCE TEXT:
                %s
                """.formatted(
                book.getTitle(),
                batchNumber,
                firstChunk,
                lastChunk,
                sourceText
        );

        return llmService.generateLongSummary(
                SUMMARY_SYSTEM_INSTRUCTION,
                prompt,
                1800
        );
    }

    private List<VectorRecord> selectCitationChunks(
            List<VectorRecord> chunks) {

        if (chunks.size() <= MAX_CITATION_CHUNKS) {
            return new ArrayList<>(chunks);
        }

        List<VectorRecord> selected = new ArrayList<>();
        int step = Math.max(1, chunks.size() / MAX_CITATION_CHUNKS);

        for (int i = 0; i < chunks.size()
                && selected.size() < MAX_CITATION_CHUNKS; i += step) {
            selected.add(chunks.get(i));
        }

        return selected;
    }

    private String numberedText(List<String> values) {
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < values.size(); i++) {
            result.append("\n\n--- PART ")
                    .append(i + 1)
                    .append(" ---\n")
                    .append(values.get(i));
        }

        return result.toString();
    }

    private String normalize(String value) {
        return value == null
                ? ""
                : value.toLowerCase(Locale.ROOT)
                        .replaceAll("[^\\p{L}\\p{N}]+", " ")
                        .trim()
                        .replaceAll("\\s+", " ");
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private boolean containsAny(String value, String... phrases) {
        for (String phrase : phrases) {
            if (value.contains(phrase)) {
                return true;
            }
        }
        return false;
    }

    private record BookMatch(List<Book> books, boolean ambiguous) {}
}