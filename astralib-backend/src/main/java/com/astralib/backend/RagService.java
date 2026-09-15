package com.astralib.backend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class RagService {

    private static final Logger logger =
            LoggerFactory.getLogger(RagService.class);

    private static final double MIN_RELEVANCE_THRESHOLD = 0.55;

    @Autowired
    private SemanticSearchService semanticSearchService;

    @Autowired
    private LlmService llmService;


    // =========================================================
    // GLOBAL RESEARCH ASSISTANT RAG
    // =========================================================

    public RagResponseDTO queryRag(
            String question,
            int topK
    ) {

        if (
                question == null ||
                question.isBlank()
        ) {
            return new RagResponseDTO(
                    "Question cannot be empty",
                    Collections.emptyList()
            );
        }

        int k =
                topK > 0
                        ? Math.min(topK, 10)
                        : 3;


        /*
         * IMPORTANT:
         *
         * Global Research Assistant uses the dedicated
         * multi-chunk RAG retrieval method.
         *
         * DO NOT use performSemanticSearch() here because
         * Smart Search intentionally deduplicates results
         * to one best chunk per resource.
         */
        List<SemanticSearchService.SearchResultDTO> retrievedChunks =
                semanticSearchService.performRagChunkSearch(
                        question,
                        k,
                        "all"
                );


        if (
                retrievedChunks == null ||
                retrievedChunks.isEmpty()
        ) {

            logger.info(
                    "RAG retrieved 0 chunks for question: {}",
                    question
            );

            return new RagResponseDTO(
                    "I could not find sufficient information in the available AstraLib documents.",
                    Collections.emptyList()
            );
        }


        // -----------------------------------------------------
        // Keep only sufficiently relevant chunks
        // -----------------------------------------------------

        List<SemanticSearchService.SearchResultDTO> relevantChunks =
                retrievedChunks
                        .stream()
                        .filter(
                                chunk ->
                                        chunk != null &&
                                        chunk.getSimilarityScore()
                                                >= MIN_RELEVANCE_THRESHOLD
                        )
                        .collect(Collectors.toList());


        // -----------------------------------------------------
        // DEBUG / VERIFICATION LOGGING
        // -----------------------------------------------------

        logger.info(
                "RAG retrieved {} total chunks and {} relevant chunks for question: {}",
                retrievedChunks.size(),
                relevantChunks.size(),
                question
        );

        for (
                SemanticSearchService.SearchResultDTO chunk :
                relevantChunks
        ) {

            logger.info(
                    "RAG chunk -> {} | score={} | document={} | section={}",
                    chunk.getChunkId(),
                    chunk.getSimilarityScore(),
                    chunk.getDocumentId(),
                    chunk.getSection()
            );
        }


        if (relevantChunks.isEmpty()) {

            return new RagResponseDTO(
                    "I could not find sufficient information in the available AstraLib documents.",
                    Collections.emptyList()
            );
        }


        // -----------------------------------------------------
        // Build citations from the same chunks sent to Gemini
        // -----------------------------------------------------

        List<CitationDTO> citations =
                buildCitations(
                        relevantChunks
                );


        // -----------------------------------------------------
        // Generate answer using multiple retrieved chunks
        // -----------------------------------------------------

        String generatedAnswer =
                generateGroundedAnswer(
                        question,
                        relevantChunks
                );


        return new RagResponseDTO(
                generatedAnswer,
                citations
        );
    }


    // =========================================================
    // DOCUMENT-SPECIFIC RAG
    // Ask This Document / Ask This Book
    // =========================================================

    public RagResponseDTO queryDocumentRag(
            String documentId,
            String question,
            int topK
    ) {

        if (
                question == null ||
                question.isBlank() ||
                documentId == null ||
                documentId.isBlank()
        ) {

            return new RagResponseDTO(
                    "Document ID and question are required",
                    Collections.emptyList()
            );
        }


        int k =
                topK > 0
                        ? Math.min(topK, 10)
                        : 3;


        /*
         * Document-level search already allows
         * multiple chunks from the same document.
         */
        List<SemanticSearchService.SearchResultDTO> retrievedChunks =
                semanticSearchService.performDocumentSemanticSearch(
                        documentId,
                        question,
                        k
                );


        if (
                retrievedChunks == null ||
                retrievedChunks.isEmpty()
        ) {

            logger.info(
                    "Document RAG retrieved 0 chunks for document {} and question: {}",
                    documentId,
                    question
            );

            return new RagResponseDTO(
                    "I could not find sufficient information in document "
                            + documentId
                            + " to answer this question.",
                    Collections.emptyList()
            );
        }


        // -----------------------------------------------------
        // Apply same relevance threshold
        // -----------------------------------------------------

        List<SemanticSearchService.SearchResultDTO> relevantChunks =
                retrievedChunks
                        .stream()
                        .filter(
                                chunk ->
                                        chunk != null &&
                                        chunk.getSimilarityScore()
                                                >= MIN_RELEVANCE_THRESHOLD
                        )
                        .collect(Collectors.toList());


        // -----------------------------------------------------
        // DEBUG / VERIFICATION LOGGING
        // -----------------------------------------------------

        logger.info(
                "Document RAG retrieved {} total chunks and {} relevant chunks for document {}",
                retrievedChunks.size(),
                relevantChunks.size(),
                documentId
        );

        for (
                SemanticSearchService.SearchResultDTO chunk :
                relevantChunks
        ) {

            logger.info(
                    "Document RAG chunk -> {} | score={} | section={}",
                    chunk.getChunkId(),
                    chunk.getSimilarityScore(),
                    chunk.getSection()
            );
        }


        if (relevantChunks.isEmpty()) {

            return new RagResponseDTO(
                    "I could not find sufficient information in document "
                            + documentId
                            + " to answer this question.",
                    Collections.emptyList()
            );
        }


        List<CitationDTO> citations =
                buildCitations(
                        relevantChunks
                );


        String generatedAnswer =
                generateGroundedAnswer(
                        question,
                        relevantChunks
                );


        return new RagResponseDTO(
                generatedAnswer,
                citations
        );
    }


    // =========================================================
    // CITATION BUILDER
    // =========================================================

    private List<CitationDTO> buildCitations(
            List<SemanticSearchService.SearchResultDTO> chunks
    ) {

        List<CitationDTO> citations =
                new ArrayList<>();


        if (chunks == null) {
            return citations;
        }


        for (
                SemanticSearchService.SearchResultDTO chunk :
                chunks
        ) {

            if (chunk == null) {
                continue;
            }


            citations.add(
                    new CitationDTO(
                            chunk.getChunkId(),
                            chunk.getDocumentId(),
                            chunk.getBookId(),
                            chunk.getTitle(),
                            chunk.getSection(),

                            chunk.getChunkIndex() != null
                                    ? chunk.getChunkIndex()
                                    : 0,

                            chunk.getAuthor(),
                            chunk.getSimilarityScore()
                    )
            );
        }


        return citations;
    }


    // =========================================================
    // GROUNDED ANSWER GENERATION
    // =========================================================

    private String generateGroundedAnswer(
            String question,
            List<SemanticSearchService.SearchResultDTO> chunks
    ) {

        if (
                chunks == null ||
                chunks.isEmpty()
        ) {

            return "I could not find sufficient information in the available AstraLib documents.";
        }


        /*
         * Build structured context for Gemini.
         *
         * Each chunk includes:
         *
         * chunk ID
         * title
         * author
         * section
         * content
         *
         * Multiple chunks are separated clearly.
         */
        String context =
                chunks
                        .stream()
                        .map(
                                chunk -> {

                                    StringBuilder builder =
                                            new StringBuilder();


                                    builder.append(
                                            "[Chunk: "
                                    );

                                    builder.append(
                                            safe(
                                                    chunk.getChunkId()
                                            )
                                    );

                                    builder.append(
                                            "]\n"
                                    );


                                    builder.append(
                                            "Title: "
                                    );

                                    builder.append(
                                            safe(
                                                    chunk.getTitle()
                                            )
                                    );

                                    builder.append(
                                            "\n"
                                    );


                                    if (
                                            chunk.getAuthor() != null &&
                                            !chunk.getAuthor().isBlank()
                                    ) {

                                        builder.append(
                                                "Author: "
                                        );

                                        builder.append(
                                                chunk.getAuthor()
                                        );

                                        builder.append(
                                                "\n"
                                        );
                                    }


                                    if (
                                            chunk.getSection() != null &&
                                            !chunk.getSection().isBlank()
                                    ) {

                                        builder.append(
                                                "Section: "
                                        );

                                        builder.append(
                                                chunk.getSection()
                                        );

                                        builder.append(
                                                "\n"
                                        );
                                    }


                                    builder.append(
                                            "Content:\n"
                                    );

                                    builder.append(
                                            safe(
                                                    chunk.getContent()
                                            )
                                    );


                                    return builder.toString();
                                }
                        )
                        .collect(
                                Collectors.joining(
                                        "\n\n------------------------------\n\n"
                                )
                        );


        logger.info(
                "Sending {} relevant chunks to LLM",
                chunks.size()
        );


        try {

            return llmService
                    .generateGroundedAnswer(
                            question,
                            context
                    );

        } catch (
                IllegalStateException e
        ) {

            logger.warn(
                    "Grounded answer generation unavailable: {}",
                    e.getMessage()
            );

            return "Sufficient information was found in AstraLib documents, but grounded answer generation is currently unavailable.";
        }
    }


    // =========================================================
    // SAFE STRING HELPER
    // =========================================================

    private String safe(
            String value
    ) {

        return value == null
                ? ""
                : value;
    }


    // =========================================================
    // RESPONSE DTO
    // =========================================================

    public static class RagResponseDTO {

        private String answer;

        private List<CitationDTO> citations;


        public RagResponseDTO() {
        }


        public RagResponseDTO(
                String answer,
                List<CitationDTO> citations
        ) {

            this.answer =
                    answer;

            this.citations =
                    citations;
        }


        public String getAnswer() {

            return answer;
        }


        public void setAnswer(
                String answer
        ) {

            this.answer =
                    answer;
        }


        public List<CitationDTO> getCitations() {

            return citations;
        }


        public void setCitations(
                List<CitationDTO> citations
        ) {

            this.citations =
                    citations;
        }
    }


    // =========================================================
    // CITATION DTO
    // =========================================================

    public static class CitationDTO {

        private String chunkId;


        /*
         * Vector/document identifier.
         *
         * Example:
         * GUTENBERG-1661
         */
        private String documentId;


        /*
         * Actual AstraLib database Book ID.
         *
         * Example:
         * 3003
         *
         * Frontend routes should use this value.
         */
        private Long bookId;


        private String title;

        private String section;

        private Integer chunkIndex;

        private String author;

        private double score;


        public CitationDTO() {
        }


        public CitationDTO(
                String chunkId,
                String documentId,
                Long bookId,
                String title,
                String section,
                Integer chunkIndex,
                String author,
                double score
        ) {

            this.chunkId =
                    chunkId;

            this.documentId =
                    documentId;

            this.bookId =
                    bookId;

            this.title =
                    title;

            this.section =
                    section;

            this.chunkIndex =
                    chunkIndex;

            this.author =
                    author;

            this.score =
                    score;
        }


        public String getChunkId() {

            return chunkId;
        }


        public void setChunkId(
                String chunkId
        ) {

            this.chunkId =
                    chunkId;
        }


        public String getDocumentId() {

            return documentId;
        }


        public void setDocumentId(
                String documentId
        ) {

            this.documentId =
                    documentId;
        }


        public Long getBookId() {

            return bookId;
        }


        public void setBookId(
                Long bookId
        ) {

            this.bookId =
                    bookId;
        }


        public String getTitle() {

            return title;
        }


        public void setTitle(
                String title
        ) {

            this.title =
                    title;
        }


        public String getSection() {

            return section;
        }


        public void setSection(
                String section
        ) {

            this.section =
                    section;
        }


        public Integer getChunkIndex() {

            return chunkIndex;
        }


        public void setChunkIndex(
                Integer chunkIndex
        ) {

            this.chunkIndex =
                    chunkIndex;
        }


        public String getAuthor() {

            return author;
        }


        public void setAuthor(
                String author
        ) {

            this.author =
                    author;
        }


        public double getScore() {

            return score;
        }


        public void setScore(
                double score
        ) {

            this.score =
                    score;
        }
    }
}