package com.astralib.backend;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Parser;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Service
public class EpubTextExtractionService {

    private static final Pattern ROMAN_SECTION_PATTERN =
            Pattern.compile(
                    "^(?i)(?:chapter\\s+)?[IVXLCDM]+\\.?\\s+.+$"
            );

    private static final Pattern NUMBERED_SECTION_PATTERN =
            Pattern.compile(
                    "^(?i)(?:chapter\\s+)?\\d+[.)]?\\s+.+$"
            );

    private static final Pattern CHAPTER_PATTERN =
            Pattern.compile(
                    "^(?i)(chapter|part|book|section)\\s+([IVXLCDM]+|\\d+).*"
            );

    /**
     * Represents one readable EPUB section/chapter.
     */
    public static class EpubSection {

        private final String href;
        private final String title;
        private final String content;
        private final int order;

        public EpubSection(
                String href,
                String title,
                String content,
                int order) {

            this.href = href;
            this.title = title;
            this.content = content;
            this.order = order;
        }

        public String getHref() {
            return href;
        }

        public String getTitle() {
            return title;
        }

        public String getContent() {
            return content;
        }

        public int getOrder() {
            return order;
        }
    }

    /**
     * Extract readable logical sections from an EPUB.
     */
    public List<EpubSection> extractSections(File epubFile) {

        List<EpubSection> sections =
                new ArrayList<>();

        if (epubFile == null) {
            return sections;
        }

        if (!epubFile.exists() || !epubFile.isFile()) {

            throw new IllegalArgumentException(
                    "EPUB file does not exist: "
                            + epubFile.getAbsolutePath()
            );
        }

        try (ZipFile zipFile = new ZipFile(epubFile)) {

            /*
             * FIX 1:
             *
             * Use the EPUB OPF spine reading order.
             *
             * Fall back to alphabetical HTML files only if
             * the EPUB does not provide a usable spine.
             */
            List<ZipEntry> htmlEntries =
                    getHtmlEntriesInReadingOrder(
                            zipFile
                    );

            int sectionOrder = 0;

            for (ZipEntry entry : htmlEntries) {

                try {

                    String html =
                            readEntry(
                                    zipFile,
                                    entry
                            );

                    if (html == null || html.isBlank()) {
                        continue;
                    }

                    Document document =
                            Jsoup.parse(html);

                    removeUnwantedElements(
                            document
                    );

                    if (document.body() == null) {
                        continue;
                    }

                    /*
                     * FIX 2:
                     *
                     * Skip Gutenberg license/boilerplate files
                     * before creating logical sections.
                     */
                    String wholeFileText =
                            normalizeText(
                                    document.body().text()
                            );

                    String fallbackTitle =
                            determineSectionTitle(
                                    document,
                                    entry.getName(),
                                    sectionOrder
                            );

                    if (isGutenbergLicense(
                            entry.getName(),
                            fallbackTitle,
                            wholeFileText)) {

                        System.out.println(
                                "Skipping Gutenberg license: "
                                        + entry.getName()
                        );

                        continue;
                    }

                    List<EpubSection> extracted =
                            splitDocumentIntoLogicalSections(
                                    document,
                                    entry.getName(),
                                    sectionOrder
                            );

                    /*
                     * Final protection in case the Gutenberg
                     * license appears as a subsection inside
                     * another XHTML file.
                     */
                    for (EpubSection section : extracted) {

                        if (isGutenbergLicense(
                                section.getHref(),
                                section.getTitle(),
                                section.getContent())) {

                            System.out.println(
                                    "Skipping Gutenberg license section: "
                                            + section.getTitle()
                            );

                            continue;
                        }

                        sections.add(
                                new EpubSection(
                                        section.getHref(),
                                        section.getTitle(),
                                        section.getContent(),
                                        sectionOrder
                                )
                        );

                        sectionOrder++;
                    }

                } catch (Exception sectionError) {

                    System.err.println(
                            "Skipping EPUB file "
                                    + entry.getName()
                                    + ": "
                                    + sectionError.getMessage()
                    );
                }
            }

            System.out.println();
            System.out.println(
                    "EPUB extraction completed. Logical sections found: "
                            + sections.size()
            );

            for (EpubSection section : sections) {

                System.out.println(
                        "EPUB SECTION "
                                + section.getOrder()
                                + " -> "
                                + section.getTitle()
                                + " | chars="
                                + section.getContent().length()
                );
            }

            return sections;

        } catch (Exception e) {

            throw new IllegalStateException(
                    "Failed to extract EPUB text from "
                            + epubFile.getAbsolutePath(),
                    e
            );
        }
    }

    // =========================================================
    // EPUB READING ORDER
    // =========================================================

    /**
     * Get XHTML/HTML entries according to the EPUB OPF spine.
     */
    private List<ZipEntry> getHtmlEntriesInReadingOrder(
            ZipFile zipFile) {

        try {

            String opfPath =
                    findOpfPath(
                            zipFile
                    );

            if (opfPath == null
                    || opfPath.isBlank()) {

                System.out.println(
                        "EPUB OPF not found. "
                                + "Using fallback file order."
                );

                return getFallbackHtmlEntries(
                        zipFile
                );
            }

            ZipEntry opfEntry =
                    zipFile.getEntry(
                            opfPath
                    );

            if (opfEntry == null) {

                System.out.println(
                        "EPUB OPF entry not found: "
                                + opfPath
                                + ". Using fallback file order."
                );

                return getFallbackHtmlEntries(
                        zipFile
                );
            }

            String opfXml =
                    readEntry(
                            zipFile,
                            opfEntry
                    );

            Document opfDocument =
                    Jsoup.parse(
                            opfXml,
                            "",
                            Parser.xmlParser()
                    );

            /*
             * Manifest:
             *
             * id -> href
             */
            Map<String, String> manifest =
                    new LinkedHashMap<>();

            for (Element item :
                    opfDocument.select(
                            "manifest > item"
                    )) {

                String id =
                        item.attr("id")
                                .trim();

                String href =
                        item.attr("href")
                                .trim();

                String mediaType =
                        item.attr("media-type")
                                .trim()
                                .toLowerCase(
                                        Locale.ROOT
                                );

                if (id.isBlank()
                        || href.isBlank()) {
                    continue;
                }

                if (mediaType.contains(
                        "application/xhtml+xml"
                )
                        || mediaType.contains(
                        "text/html"
                )
                        || isHtmlEntry(href)) {

                    manifest.put(
                            id,
                            href
                    );
                }
            }

            if (manifest.isEmpty()) {

                System.out.println(
                        "EPUB manifest contains no readable HTML. "
                                + "Using fallback order."
                );

                return getFallbackHtmlEntries(
                        zipFile
                );
            }

            String opfDirectory =
                    getDirectory(
                            opfPath
                    );

            List<ZipEntry> ordered =
                    new ArrayList<>();

            Set<String> added =
                    new HashSet<>();

            /*
             * Spine defines actual reading order.
             */
            for (Element itemRef :
                    opfDocument.select(
                            "spine > itemref"
                    )) {

                String idRef =
                        itemRef.attr(
                                "idref"
                        ).trim();

                String href =
                        manifest.get(
                                idRef
                        );

                if (href == null
                        || href.isBlank()) {
                    continue;
                }

                String resolvedPath =
                        resolveZipPath(
                                opfDirectory,
                                href
                        );

                ZipEntry htmlEntry =
                        zipFile.getEntry(
                                resolvedPath
                        );

                if (htmlEntry == null) {

                    /*
                     * Some EPUBs contain URL encoded names.
                     * We keep this simple and just skip if
                     * the exact ZIP entry cannot be resolved.
                     */
                    continue;
                }

                if (!isHtmlEntry(
                        htmlEntry.getName()
                )) {
                    continue;
                }

                if (added.add(
                        htmlEntry.getName()
                )) {

                    ordered.add(
                            htmlEntry
                    );
                }
            }

            if (!ordered.isEmpty()) {

                System.out.println(
                        "Using EPUB OPF spine order. "
                                + "Readable spine files: "
                                + ordered.size()
                );

                return ordered;
            }

            System.out.println(
                    "EPUB spine produced no readable files. "
                            + "Using fallback order."
            );

            return getFallbackHtmlEntries(
                    zipFile
            );

        } catch (Exception e) {

            System.err.println(
                    "Could not read EPUB spine: "
                            + e.getMessage()
                            + ". Using fallback order."
            );

            return getFallbackHtmlEntries(
                    zipFile
            );
        }
    }

    /**
     * META-INF/container.xml tells us where the OPF package file is.
     */
    private String findOpfPath(
            ZipFile zipFile)
            throws Exception {

        ZipEntry containerEntry =
                zipFile.getEntry(
                        "META-INF/container.xml"
                );

        if (containerEntry == null) {
            return null;
        }

        String containerXml =
                readEntry(
                        zipFile,
                        containerEntry
                );

        if (containerXml == null
                || containerXml.isBlank()) {

            return null;
        }

        Document containerDocument =
                Jsoup.parse(
                        containerXml,
                        "",
                        Parser.xmlParser()
                );

        Element rootFile =
                containerDocument.selectFirst(
                        "rootfile"
                );

        if (rootFile == null) {
            return null;
        }

        String fullPath =
                rootFile.attr(
                        "full-path"
                ).trim();

        if (fullPath.isBlank()) {
            return null;
        }

        return normalizeZipPath(
                fullPath
        );
    }

    /**
     * Fallback only when OPF/spine information cannot be used.
     */
    private List<ZipEntry> getFallbackHtmlEntries(
            ZipFile zipFile) {

        List<ZipEntry> entries =
            new ArrayList<>();

        zipFile.stream()
            .filter(entry ->
                    !entry.isDirectory()
                            && isHtmlEntry(
                                    entry.getName()
                            )
            )
            .sorted(
                    Comparator.comparing(
                            ZipEntry::getName
                    )
            )
            .forEach(entry ->
                    entries.add(entry)
            );

        return entries;
    }

    private String getDirectory(
            String path) {

        if (path == null
                || path.isBlank()) {

            return "";
        }

        String normalized =
                normalizeZipPath(
                        path
                );

        int lastSlash =
                normalized.lastIndexOf('/');

        if (lastSlash < 0) {
            return "";
        }

        return normalized.substring(
                0,
                lastSlash + 1
        );
    }

    private String resolveZipPath(
            String baseDirectory,
            String href) {

        String cleanedHref =
                href;

        int hash =
                cleanedHref.indexOf('#');

        if (hash >= 0) {

            cleanedHref =
                    cleanedHref.substring(
                            0,
                            hash
                    );
        }

        int query =
                cleanedHref.indexOf('?');

        if (query >= 0) {

            cleanedHref =
                    cleanedHref.substring(
                            0,
                            query
                    );
        }

        String combined =
                (baseDirectory == null
                        ? ""
                        : baseDirectory)
                        + cleanedHref;

        try {

            return normalizeZipPath(
                    Paths.get(
                                    combined
                            )
                            .normalize()
                            .toString()
            );

        } catch (Exception ignored) {

            return normalizeZipPath(
                    combined
            );
        }
    }

    private String normalizeZipPath(
            String path) {

        if (path == null) {
            return "";
        }

        return path
                .replace('\\', '/')
                .replaceAll("^/+", "")
                .trim();
    }

    // =========================================================
    // LOGICAL SECTION SPLITTING
    // =========================================================

    private List<EpubSection> splitDocumentIntoLogicalSections(
            Document document,
            String href,
            int startingOrder) {

        List<EpubSection> result =
                new ArrayList<>();

        Element body =
                document.body();

        if (body == null) {
            return result;
        }

        List<Element> structuralHeadings =
                body.select(
                                "h1, h2, h3"
                        )
                        .stream()
                        .filter(
                                this::isMeaningfulStructuralHeading
                        )
                        .toList();

        /*
         * If there is only one meaningful heading,
         * preserve the file as one logical section.
         */
        if (structuralHeadings.size() <= 1) {

            String content =
                    normalizeText(
                            body.text()
                    );

            if (content.length() < 80) {
                return result;
            }

            String title =
                    determineSectionTitle(
                            document,
                            href,
                            startingOrder
                    );

            if (!isGutenbergLicense(
                    href,
                    title,
                    content)) {

                result.add(
                        new EpubSection(
                                href,
                                title,
                                content,
                                startingOrder
                        )
                );
            }

            return result;
        }

        String defaultTitle =
                determineSectionTitle(
                        document,
                        href,
                        startingOrder
                );

        String currentTitle =
                defaultTitle;

        StringBuilder currentContent =
                new StringBuilder();

        int currentOrder =
                startingOrder;

        boolean foundStructuralHeading =
                false;

        for (Node child :
                body.childNodes()) {

            if (child instanceof Element element) {

                List<Element> headingsInNode =
                        new ArrayList<>();

                if (isHeadingElement(element)
                        && isMeaningfulStructuralHeading(
                                element
                        )) {

                    headingsInNode.add(
                            element
                    );

                } else {

                    headingsInNode.addAll(
                            element.select(
                                            "h1, h2, h3"
                                    )
                                    .stream()
                                    .filter(
                                            this::isMeaningfulStructuralHeading
                                    )
                                    .toList()
                    );
                }

                if (!headingsInNode.isEmpty()) {

                    if (headingsInNode.size() == 1) {

                        Element heading =
                                headingsInNode.get(0);

                        String headingText =
                                normalizeText(
                                        heading.text()
                                );

                        if (!headingText.isBlank()) {

                            if (foundStructuralHeading
                                    || currentContent.length() > 0) {

                                currentOrder =
                                        addSectionIfUseful(
                                                result,
                                                href,
                                                currentTitle,
                                                currentContent.toString(),
                                                currentOrder
                                        );
                            }

                            currentTitle =
                                    headingText;

                            currentContent =
                                    new StringBuilder();

                            foundStructuralHeading =
                                    true;

                            Element clone =
                                    element.clone();

                            clone.select(
                                    "h1, h2, h3"
                            ).remove();

                            String remainingText =
                                    normalizeText(
                                            clone.text()
                                    );

                            if (!remainingText.isBlank()) {

                                currentContent
                                        .append(
                                                remainingText
                                        )
                                        .append(' ');
                            }

                            continue;
                        }
                    }

                    if (headingsInNode.size() > 1) {

                        currentOrder =
                                processContainer(
                                        element,
                                        href,
                                        result,
                                        currentTitle,
                                        currentContent,
                                        currentOrder
                                );

                        currentContent =
                                new StringBuilder();

                        foundStructuralHeading =
                                true;

                        continue;
                    }
                }

                String text =
                        normalizeText(
                                element.text()
                        );

                if (!text.isBlank()) {

                    currentContent
                            .append(text)
                            .append(' ');
                }

            } else if (child instanceof TextNode textNode) {

                String text =
                        normalizeText(
                                textNode.text()
                        );

                if (!text.isBlank()) {

                    currentContent
                            .append(text)
                            .append(' ');
                }
            }
        }

        addSectionIfUseful(
                result,
                href,
                currentTitle,
                currentContent.toString(),
                currentOrder
        );

        if (result.isEmpty()) {

            String content =
                    normalizeText(
                            body.text()
                    );

            if (content.length() >= 80
                    && !isGutenbergLicense(
                            href,
                            defaultTitle,
                            content)) {

                result.add(
                        new EpubSection(
                                href,
                                defaultTitle,
                                content,
                                startingOrder
                        )
                );
            }
        }

        return result;
    }

    private int processContainer(
            Element container,
            String href,
            List<EpubSection> result,
            String fallbackTitle,
            StringBuilder previousContent,
            int startingOrder) {

        int order =
                startingOrder;

        String currentTitle =
                fallbackTitle;

        StringBuilder currentContent =
                new StringBuilder();

        if (previousContent != null
                && previousContent.length() > 0) {

            currentContent.append(
                    previousContent
            );
        }

        for (Node node :
                container.childNodes()) {

            if (node instanceof Element element) {

                if (isHeadingElement(element)
                        && isMeaningfulStructuralHeading(
                                element
                        )) {

                    String heading =
                            normalizeText(
                                    element.text()
                            );

                    order =
                            addSectionIfUseful(
                                    result,
                                    href,
                                    currentTitle,
                                    currentContent.toString(),
                                    order
                            );

                    currentTitle =
                            heading;

                    currentContent =
                            new StringBuilder();

                    continue;
                }

                List<Element> nestedHeadings =
                        element.select(
                                        "h1, h2, h3"
                                )
                                .stream()
                                .filter(
                                        this::isMeaningfulStructuralHeading
                                )
                                .toList();

                if (!nestedHeadings.isEmpty()) {

                    order =
                            processContainer(
                                    element,
                                    href,
                                    result,
                                    currentTitle,
                                    currentContent,
                                    order
                            );

                    currentContent =
                            new StringBuilder();

                    continue;
                }

                String text =
                        normalizeText(
                                element.text()
                        );

                if (!text.isBlank()) {

                    currentContent
                            .append(text)
                            .append(' ');
                }

            } else if (node instanceof TextNode textNode) {

                String text =
                        normalizeText(
                                textNode.text()
                        );

                if (!text.isBlank()) {

                    currentContent
                            .append(text)
                            .append(' ');
                }
            }
        }

        return addSectionIfUseful(
                result,
                href,
                currentTitle,
                currentContent.toString(),
                order
        );
    }

    private int addSectionIfUseful(
            List<EpubSection> sections,
            String href,
            String title,
            String content,
            int order) {

        String cleaned =
                normalizeText(
                        content
                );

        if (cleaned.length() < 80) {
            return order;
        }

        String safeTitle =
                normalizeText(
                        title
                );

        if (safeTitle.isBlank()) {

            safeTitle =
                    "Section "
                            + (order + 1);
        }

        /*
         * Do not keep Gutenberg license content.
         */
        if (isGutenbergLicense(
                href,
                safeTitle,
                cleaned)) {

            return order;
        }

        sections.add(
                new EpubSection(
                        href,
                        safeTitle,
                        cleaned,
                        order
                )
        );

        return order + 1;
    }

    // =========================================================
    // GUTENBERG LICENSE FILTER
    // =========================================================

    private boolean isGutenbergLicense(
            String href,
            String title,
            String content) {

        String safeHref =
                normalizeForComparison(
                        href
                );

        String safeTitle =
                normalizeForComparison(
                        title
                );

        String safeContent =
                normalizeForComparison(
                        content
                );

        /*
         * Strong title signals.
         */
        if (safeTitle.contains(
                "full project gutenberg license"
        )) {

            return true;
        }

        if (safeTitle.contains(
                "project gutenberg license"
        )) {

            return true;
        }

        /*
         * Common Gutenberg file names.
         */
        if (safeHref.contains("license")
                && safeContent.contains(
                        "project gutenberg"
                )) {

            return true;
        }

        /*
         * Strong content markers.
         */
        if (safeContent.contains(
                "the full project gutenberg license"
        )) {

            return true;
        }

        if (safeContent.contains(
                "full project gutenberg license"
        )) {

            return true;
        }

        if (safeContent.contains(
                "project gutenberg literary archive foundation"
        )
                && safeContent.contains(
                        "license"
                )) {

            return true;
        }

        return false;
    }

    private String normalizeForComparison(
            String value) {

        if (value == null) {
            return "";
        }

        return value
                .replace('\u00A0', ' ')
                .replace('’', '\'')
                .replace('‘', '\'')
                .replace('“', '"')
                .replace('”', '"')
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase(
                        Locale.ROOT
                );
    }

    // =========================================================
    // HEADING DETECTION
    // =========================================================

    private boolean isMeaningfulStructuralHeading(
            Element element) {

        if (element == null) {
            return false;
        }

        String text =
                normalizeText(
                        element.text()
                );

        if (text.isBlank()) {
            return false;
        }

        if (text.length() > 180) {
            return false;
        }

        String lower =
                text.toLowerCase(
                        Locale.ROOT
                );

        if (lower.equals("contents")
                || lower.equals("table of contents")
                || lower.equals("navigation")
                || lower.equals("cover")
                || lower.equals("title page")) {

            return false;
        }

        if (ROMAN_SECTION_PATTERN
                .matcher(text)
                .matches()) {

            return true;
        }

        if (NUMBERED_SECTION_PATTERN
                .matcher(text)
                .matches()) {

            return true;
        }

        if (CHAPTER_PATTERN
                .matcher(text)
                .matches()) {

            return true;
        }

        return element.tagName()
                .equalsIgnoreCase(
                        "h1"
                );
    }

    private boolean isHeadingElement(
            Element element) {

        if (element == null) {
            return false;
        }

        String tag =
                element.tagName()
                        .toLowerCase(
                                Locale.ROOT
                        );

        return tag.equals("h1")
                || tag.equals("h2")
                || tag.equals("h3");
    }

    // =========================================================
    // FULL TEXT
    // =========================================================

    public String extractFullText(
            File epubFile) {

        List<EpubSection> sections =
                extractSections(
                        epubFile
                );

        StringBuilder fullText =
                new StringBuilder();

        for (EpubSection section :
                sections) {

            if (!section.getTitle()
                    .isBlank()) {

                fullText.append(
                        section.getTitle()
                );

                fullText.append(
                        "\n\n"
                );
            }

            fullText.append(
                    section.getContent()
            );

            fullText.append(
                    "\n\n"
            );
        }

        return fullText
                .toString()
                .trim();
    }

    // =========================================================
    // FILE HELPERS
    // =========================================================

    private boolean isHtmlEntry(
            String name) {

        if (name == null) {
            return false;
        }

        String lower =
                name.toLowerCase(
                        Locale.ROOT
                );

        return lower.endsWith(".xhtml")
                || lower.endsWith(".html")
                || lower.endsWith(".htm");
    }

    private String readEntry(
            ZipFile zipFile,
            ZipEntry entry)
            throws Exception {

        try (
                InputStream input =
                        zipFile.getInputStream(
                                entry
                        );

                ByteArrayOutputStream output =
                        new ByteArrayOutputStream()
        ) {

            byte[] buffer =
                    new byte[8192];

            int bytesRead;

            while (
                    (bytesRead =
                            input.read(buffer))
                            != -1
            ) {

                output.write(
                        buffer,
                        0,
                        bytesRead
                );
            }

            return output.toString(
                    StandardCharsets.UTF_8
            );
        }
    }

    private void removeUnwantedElements(
            Document document) {

        document.select(
                "script, style, nav, noscript, svg"
        ).remove();
    }

    private String determineSectionTitle(
            Document document,
            String fileName,
            int order) {

        String[] selectors = {
                "h1",
                "h2",
                "h3",
                "title"
        };

        for (String selector :
                selectors) {

            Element element =
                    document.selectFirst(
                            selector
                    );

            if (element != null) {

                String text =
                        normalizeText(
                                element.text()
                        );

                if (!text.isBlank()) {
                    return text;
                }
            }
        }

        String fallback =
                fileName;

        int slash =
                fallback.lastIndexOf('/');

        if (slash >= 0) {

            fallback =
                    fallback.substring(
                            slash + 1
                    );
        }

        fallback =
                fallback.replaceAll(
                        "(?i)\\.(xhtml|html|htm)$",
                        ""
                );

        fallback =
                normalizeText(
                        fallback
                );

        if (!fallback.isBlank()) {
            return fallback;
        }

        return "Section "
                + (order + 1);
    }

    private String normalizeText(
            String text) {

        if (text == null) {
            return "";
        }

        return text
                .replace('\u00A0', ' ')
                .replace('’', '\'')
                .replace('‘', '\'')
                .replace('“', '"')
                .replace('”', '"')
                .replaceAll("\\s+", " ")
                .trim();
    }
}