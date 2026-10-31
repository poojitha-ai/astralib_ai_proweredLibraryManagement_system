package com.astralib.backend;

/**
 * One readable, section-aware slice of a book, independent of the file
 * format it came from.
 *
 * EPUB chapters, PDF chapter headings and HTML headings all map onto this
 * contract so that chunking, embedding and vector storage stay a single
 * pipeline. Nothing downstream needs to know which extractor ran.
 */
public interface ExtractedSection {

    /**
     * Human-readable heading, such as "Chapter 3: Objects" or
     * "Pages 41-50" when the source has no usable heading.
     */
    String getTitle();

    /**
     * The real prose of this section. Never a placeholder.
     */
    String getContent();

    /**
     * Zero-based order of the section inside the book.
     */
    int getOrder();
}
