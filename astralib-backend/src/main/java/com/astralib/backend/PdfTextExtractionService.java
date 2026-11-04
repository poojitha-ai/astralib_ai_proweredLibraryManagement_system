package com.astralib.backend;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts readable sections from a PDF book.
 *
 * Open textbooks are frequently published only as PDF, so the collection
 * needs a real text extractor rather than metadata-only records. The text
 * layer is read with PDFBox and grouped into chapters wherever the running
 * header of a page looks like a chapter heading; otherwise pages are grouped
 * into fixed-size windows so that no section becomes unmanageably large.
 *
 * Only the text layer is used. A scanned PDF without one yields no text and
 * the ingestion pipeline reports that as a failure instead of pretending the
 * book was indexed.
 */
@Service
public class PdfTextExtractionService {

    private static final Logger logger =
            LoggerFactory.getLogger(PdfTextExtractionService.class);

    /**
     * "Chapter 4", "CHAPTER 12. Objects", "4.2 Inheritance", "Part II".
     * Kept deliberately narrow: a false positive only starts a new section,
     * it never invents text.
     */
    private static final Pattern HEADING = Pattern.compile(
            "^\\s*(?:chapter\\s+(?:\\d+|[IVXLCDM]+)\\b"
                    + "|part\\s+(?:\\d+|[IVXLCDM]+)\\b"
                    + "|\\d+\\.\\d+\\s+\\p{Lu}\\S*)"
                    + "(.{0,80})$",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Pages are grouped at most this large when no headings are found.
     * Twenty pages is roughly one chapter of prose, and keeps a section
     * comfortably inside the chunker's working size.
     */
    private static final int PAGES_PER_FALLBACK_SECTION = 20;

    /**
     * A single page contributing less than this many characters is treated
     * as a cover, blank page, or figure page and is skipped.
     */
    private static final int MIN_PAGE_CHARACTERS = 40;

    public List<ExtractedSection> extractSections(File pdfFile) throws IOException {

        List<ExtractedSection> sections = new ArrayList<>();

        if (pdfFile == null) {
            return sections;
        }

        if (!pdfFile.exists() || !pdfFile.isFile()) {
            throw new IllegalArgumentException(
                    "PDF file does not exist: " + pdfFile.getAbsolutePath()
            );
        }

        try (PDDocument document = Loader.loadPDF(pdfFile)) {

            int pageCount = document.getNumberOfPages();

            if (pageCount == 0) {
                return sections;
            }

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setLineSeparator("\n");

            StringBuilder current = new StringBuilder();
            String currentTitle = null;
            int currentStartPage = 1;
            int sectionOrder = 0;
            int pagesInSection = 0;

            for (int page = 1; page <= pageCount; page++) {

                stripper.setStartPage(page);
                stripper.setEndPage(page);

                String pageText = stripper.getText(document);

                if (pageText == null || pageText.isBlank()) {
                    continue;
                }

                String trimmed = pageText.trim();

                if (trimmed.length() < MIN_PAGE_CHARACTERS) {
                    continue;
                }

                String heading = findHeading(trimmed);
                boolean startNew =
                        heading != null
                                || pagesInSection >= PAGES_PER_FALLBACK_SECTION;

                if (current.length() > 0 && startNew) {
                    sections.add(
                            new SimpleSection(
                                    sectionTitle(currentTitle, currentStartPage, page - 1),
                                    current.toString().trim(),
                                    sectionOrder++
                            )
                    );
                    current.setLength(0);
                    pagesInSection = 0;
                }

                if (current.length() == 0) {
                    currentStartPage = page;

                    if (heading != null) {
                        currentTitle = heading;
                    } else {
                        currentTitle = null;
                    }
                }

                if (current.length() > 0) {
                    current.append("\n\n");
                }

                current.append(trimmed);
                pagesInSection++;
            }

            if (current.length() > 0) {
                sections.add(
                        new SimpleSection(
                                sectionTitle(currentTitle, currentStartPage, pageCount),
                                current.toString().trim(),
                                sectionOrder
                        )
                );
            }

            logger.info(
                    "PDF extraction: {} pages -> {} sections ({})",
                    pageCount,
                    sections.size(),
                    pdfFile.getName()
            );

            return sections;
        }
    }

    /**
     * Returns the heading text when the first non-blank line of the page
     * looks like a chapter or numbered section heading.
     */
    private String findHeading(String pageText) {

        String[] lines = pageText.split("\n");

        int inspected = 0;

        for (String line : lines) {

            String candidate = line.trim();

            if (candidate.isEmpty()) {
                continue;
            }

            if (++inspected > 3) {
                break;
            }

            if (candidate.length() > 120) {
                continue;
            }

            Matcher matcher = HEADING.matcher(candidate);

            if (matcher.matches()) {
                return candidate;
            }
        }

        return null;
    }

    private String sectionTitle(String heading, int startPage, int endPage) {

        if (heading != null && !heading.isBlank()) {
            return heading;
        }

        if (startPage == endPage) {
            return "Page " + startPage;
        }

        return "Pages " + startPage + "-" + endPage;
    }
}
