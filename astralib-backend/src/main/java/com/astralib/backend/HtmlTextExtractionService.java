package com.astralib.backend;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Extracts readable sections from an HTML textbook edition.
 *
 * Several open textbooks are published only as web editions. Their chapters
 * are downloaded as a single saved page per book (already reduced to the
 * official chapter bodies) and this service turns that markup into sections at
 * heading boundaries, discarding navigation, scripts and other site chrome.
 *
 * The saved file is the archival copy of what was licensed; nothing is fetched
 * at ingestion time, so a later change on the publisher's site cannot silently
 * alter what the library indexed.
 */
@Service
public class HtmlTextExtractionService {

    private static final Logger logger =
            LoggerFactory.getLogger(HtmlTextExtractionService.class);

    /** Minimum characters before a heading is allowed to open a new section. */
    private static final int MIN_SECTION_CHARACTERS = 200;

    public List<ExtractedSection> extractSections(File htmlFile) throws IOException {

        List<ExtractedSection> sections = new ArrayList<>();

        if (htmlFile == null) {
            return sections;
        }

        if (!htmlFile.exists() || !htmlFile.isFile()) {
            throw new IllegalArgumentException(
                    "HTML file does not exist: " + htmlFile.getAbsolutePath()
            );
        }

        String html = Files.readString(
                htmlFile.toPath(),
                StandardCharsets.UTF_8
        );

        if (html.isBlank()) {
            return sections;
        }

        Document document = Jsoup.parse(html, "UTF-8");

        // Site chrome and anything that is not book prose.
        document.select(
                "script, style, noscript, nav, header, footer, aside, form, iframe,"
                        + " svg, .mw-editsection, .mw-jump-link, .printfooter, .navbox,"
                        + " .toc, .sidebar, .navigation, .menu, .breadcrumb"
        ).remove();

        Element root = pickContentRoot(document);

        if (root == null) {
            return sections;
        }

        StringBuilder current = new StringBuilder();
        String currentTitle = null;
        int order = 0;

        Set<Element> consumed = new HashSet<>();

        Elements blocks = root.select("h1, h2, h3, p, pre, blockquote, li, td");

        for (Element block : blocks) {

            if (hasConsumedAncestor(block, consumed)) {
                continue;
            }

            consumed.add(block);

            String text = block.text().trim();

            if (text.isEmpty()) {
                continue;
            }

            boolean isHeading =
                    block.tagName().equals("h1")
                            || block.tagName().equals("h2")
                            || block.tagName().equals("h3");

            if (
                    isHeading
                            && current.length() >= MIN_SECTION_CHARACTERS
            ) {
                sections.add(
                        new SimpleSection(
                                sectionTitle(currentTitle, order),
                                current.toString().trim(),
                                order++
                        )
                );
                current.setLength(0);
                currentTitle = text;
                continue;
            }

            if (currentTitle == null && text.length() <= 120) {
                currentTitle = text;
            }

            if (block.tagName().equals("pre")) {
                current.append("\n\n").append(block.wholeText().trim());
            } else {
                current.append("\n\n").append(text);
            }
        }

        if (!current.toString().isBlank()) {
            sections.add(
                    new SimpleSection(
                            sectionTitle(currentTitle, order),
                            current.toString().trim(),
                            order
                    )
            );
        }

        logger.info(
                "HTML extraction: {} sections from {}",
                sections.size(),
                htmlFile.getName()
        );

        return sections;
    }

    private Element pickContentRoot(Document document) {

        String[] selectors = {
                "article",
                "main",
                "#mw-content-text",
                ".mw-parser-output",
                "#content",
                "body"
        };

        for (String selector : selectors) {
            Element candidate = document.selectFirst(selector);

            if (candidate != null) {
                return candidate;
            }
        }

        return document.body();
    }

    private boolean hasConsumedAncestor(Element element, Set<Element> consumed) {

        Element parent = element.parent();

        while (parent != null) {
            if (consumed.contains(parent)) {
                return true;
            }
            parent = parent.parent();
        }

        return false;
    }

    private String sectionTitle(String title, int order) {

        if (title != null && !title.isBlank()) {
            return title;
        }

        return "Section " + (order + 1);
    }
}
