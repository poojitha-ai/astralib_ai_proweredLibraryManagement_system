package com.astralib.backend;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DocumentChunkerService {

    @Value("${chunking.chunk-size:500}")
    private int chunkSize = 500;

    @Value("${chunking.overlap:50}")
    private int overlap = 50;

    // =========================================================
    // EXISTING GENERAL DOCUMENT CHUNKING
    // =========================================================

    public List<Chunk> chunkText(
            String docId,
            String title,
            String author,
            String category,
            String docType,
            String text) {

        return chunkTextWithSection(
                docId,
                title,
                author,
                category,
                docType,
                "Full Document",
                text,
                0
        );
    }

    // =========================================================
    // NEW:
    // CHAPTER-AWARE CHUNKING
    // =========================================================

    public List<Chunk> chunkTextWithSection(
            String docId,
            String title,
            String author,
            String category,
            String docType,
            String sectionName,
            String text,
            int startingChunkIndex) {

        List<Chunk> chunks =
                new ArrayList<>();

        if (
                text == null ||
                text.isBlank()
        ) {
            return chunks;
        }

        String cleaned =
                cleanText(text);

        if (cleaned.isBlank()) {
            return chunks;
        }

        String safeSection =
                sectionName == null ||
                sectionName.isBlank()
                        ? "Main"
                        : sectionName.trim();

        int chunkSeq =
                startingChunkIndex;

        // Small section = one chunk
        if (
                cleaned.length() <=
                chunkSize
        ) {

            String chunkId =
                    createChunkId(
                            docId,
                            chunkSeq
                    );

            chunks.add(
                    new Chunk(
                            chunkId,
                            docId,
                            docType,
                            title,
                            author,
                            category,
                            safeSection,
                            cleaned
                    )
            );

            return chunks;
        }

        int index = 0;

        int textLength =
                cleaned.length();

        while (
                index < textLength
        ) {

            int targetEnd =
                    Math.min(
                            index + chunkSize,
                            textLength
                    );

            // ---------------------------------------------
            // Prefer ending at sentence boundary
            // ---------------------------------------------

            if (
                    targetEnd <
                    textLength
            ) {

                int sentenceBoundary =
                        findSentenceBoundary(
                                cleaned,
                                index,
                                targetEnd
                        );

                if (
                        sentenceBoundary >
                        index
                ) {
                    targetEnd =
                            sentenceBoundary;
                } else {

                    // -------------------------------------
                    // Otherwise at least avoid word split
                    // -------------------------------------

                    int wordBoundary =
                            findWordBoundary(
                                    cleaned,
                                    index,
                                    targetEnd
                            );

                    if (
                            wordBoundary >
                            index
                    ) {
                        targetEnd =
                                wordBoundary;
                    }
                }
            }

            String content =
                    cleaned
                            .substring(
                                    index,
                                    targetEnd
                            )
                            .trim();

            if (
                    !content.isBlank()
            ) {

                String chunkId =
                        createChunkId(
                                docId,
                                chunkSeq
                        );

                chunks.add(
                        new Chunk(
                                chunkId,
                                docId,
                                docType,
                                title,
                                author,
                                category,
                                safeSection,
                                content
                        )
                );

                chunkSeq++;
            }

            if (
                    targetEnd >=
                    textLength
            ) {
                break;
            }

            int nextStart =
                    Math.max(
                            targetEnd - overlap,
                            index + 1
                    );

            index =
                    nextStart;
        }

        return chunks;
    }

    // =========================================================
    // CLEAN TEXT
    // =========================================================

    private String cleanText(
            String text) {

        return text
                .replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    // =========================================================
    // CHUNK ID
    // =========================================================

    private String createChunkId(
            String docId,
            int index) {

        return String.format(
                "%s-CHK-%05d",
                docId,
                index
        );
    }

    // =========================================================
    // SENTENCE BOUNDARY
    // =========================================================

    private int findSentenceBoundary(
            String text,
            int start,
            int targetEnd) {

        int minimum =
                Math.max(
                        start,
                        targetEnd - 120
                );

        for (
                int i = targetEnd - 1;
                i >= minimum;
                i--
        ) {

            char ch =
                    text.charAt(i);

            if (
                    ch == '.'
                            || ch == '!'
                            || ch == '?'
            ) {

                if (
                        i + 1 <
                        text.length()
                ) {

                    return i + 1;
                }
            }
        }

        return -1;
    }

    // =========================================================
    // WORD BOUNDARY
    // =========================================================

    private int findWordBoundary(
            String text,
            int start,
            int targetEnd) {

        int boundary =
                targetEnd;

        while (
                boundary > start &&
                !Character.isWhitespace(
                        text.charAt(
                                boundary - 1
                        )
                )
        ) {

            boundary--;
        }

        return boundary;
    }

    // =========================================================
    // CHUNK DTO
    // =========================================================

    public static class Chunk {

        private String chunkId;

        private String documentId;

        private String documentType;

        private String title;

        private String author;

        private String category;

        private String section;

        private String content;

        public Chunk(
                String chunkId,
                String documentId,
                String documentType,
                String title,
                String author,
                String category,
                String section,
                String content) {

            this.chunkId =
                    chunkId;

            this.documentId =
                    documentId;

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

            this.content =
                    content;
        }

        public String getChunkId() {
            return chunkId;
        }

        public String getDocumentId() {
            return documentId;
        }

        public String getDocumentType() {
            return documentType;
        }

        public String getTitle() {
            return title;
        }

        public String getAuthor() {
            return author;
        }

        public String getCategory() {
            return category;
        }

        public String getSection() {
            return section;
        }

        public String getContent() {
            return content;
        }
    }
}