package com.astralib.backend;

/**
 * A plain section, used by the PDF and HTML extractors.
 */
public class SimpleSection implements ExtractedSection {

    private final String title;
    private final String content;
    private final int order;

    public SimpleSection(String title, String content, int order) {
        this.title = title;
        this.content = content;
        this.order = order;
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public String getContent() {
        return content;
    }

    @Override
    public int getOrder() {
        return order;
    }
}
