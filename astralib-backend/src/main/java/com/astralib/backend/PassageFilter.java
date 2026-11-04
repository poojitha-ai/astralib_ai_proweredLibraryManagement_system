package com.astralib.backend;

import java.util.Locale;

/**
 * Decides whether a stored row is a passage of a book or just a record of one.
 *
 * The collection contains 3,145 legacy rows whose "content" is a title block
 * ("Title: ... Author: ... Description: ...") rather than book prose. They
 * were written before ingestion refused to fabricate vectors, and they are a
 * real hazard: matching one lets the UI cite a book that has no readable text.
 *
 * They are not deleted - the vectors are preserved - but they are never
 * retrieved, and neither are heading-sized fragments that carry no prose.
 */
public final class PassageFilter {

    /** Shortest fragment treated as real prose. */
    private static final int MIN_PASSAGE_CHARACTERS = 60;

    private PassageFilter() {
    }

    public static boolean isGroundedPassage(VectorRecord record) {

        if (record == null || record.getContent() == null) {
            return false;
        }

        String content = record.getContent().trim();

        if (content.length() < MIN_PASSAGE_CHARACTERS) {
            return false;
        }

        return !isMetadataStub(content, record.getSection());
    }

    /**
     * A metadata stub is the title block ingested for a book whose text was
     * never available. It starts with "Title:" and carries an "Author:" line
     * within the first few lines.
     */
    public static boolean isMetadataStub(String content, String section) {

        if (content == null) {
            return false;
        }

        String head = content.trim();
        String lower = head.toLowerCase(Locale.ROOT);

        if (!lower.startsWith("title:")) {
            return false;
        }

        String firstLines = head.length() > 400 ? head.substring(0, 400) : head;

        boolean hasAuthor = firstLines.toLowerCase(Locale.ROOT).contains("author:");

        boolean fullDocument =
                section == null || section.isBlank()
                        || "full document".equalsIgnoreCase(section.trim());

        return hasAuthor && fullDocument;
    }
}
