package com.astralib.backend;

import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * One entry point for "turn this stored book file into sections".
 *
 * Every supported format runs through the same extractor contract, so
 * chunking, embedding and vector storage stay format-neutral and there is
 * only ever one RAG implementation.
 */
@Service
public class DocumentExtractionService {

    public static final String FORMAT_EPUB = "application/epub+zip";
    public static final String FORMAT_PDF = "application/pdf";
    public static final String FORMAT_HTML = "text/html";

    private final EpubTextExtractionService epubTextExtractionService;
    private final PdfTextExtractionService pdfTextExtractionService;
    private final HtmlTextExtractionService htmlTextExtractionService;

    public DocumentExtractionService(
            EpubTextExtractionService epubTextExtractionService,
            PdfTextExtractionService pdfTextExtractionService,
            HtmlTextExtractionService htmlTextExtractionService) {

        this.epubTextExtractionService = epubTextExtractionService;
        this.pdfTextExtractionService = pdfTextExtractionService;
        this.htmlTextExtractionService = htmlTextExtractionService;
    }

    /**
     * Whether the pipeline can read this file. Used to decide whether a
     * resource counts as readable and AI-searchable.
     */
    public boolean supports(String fileName, String format) {

        String extension = extensionOf(fileName);

        if ("epub".equals(extension) || "pdf".equals(extension)) {
            return true;
        }

        if ("html".equals(extension) || "htm".equals(extension)) {
            return true;
        }

        if (format == null) {
            return false;
        }

        String normalised = format.trim().toLowerCase(Locale.ROOT);

        return FORMAT_EPUB.equals(normalised)
                || FORMAT_PDF.equals(normalised)
                || FORMAT_HTML.equals(normalised);
    }

    /**
     * MIME type for a stored file, derived from its extension.
     */
    public String formatOf(String fileName) {

        String extension = extensionOf(fileName);

        return switch (extension) {
            case "epub" -> FORMAT_EPUB;
            case "pdf" -> FORMAT_PDF;
            case "html", "htm" -> FORMAT_HTML;
            default -> "application/octet-stream";
        };
    }

    public String extensionOf(String fileName) {

        if (fileName == null) {
            return "";
        }

        String name = fileName.trim().toLowerCase(Locale.ROOT);

        int dot = name.lastIndexOf('.');

        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }

        return name.substring(dot + 1);
    }

    /**
     * Human label for the stored format, used in reports and in the reader
     * action so a PDF is never offered the EPUB viewer.
     */
    public String labelOf(String fileName) {

        return switch (extensionOf(fileName)) {
            case "epub" -> "EPUB";
            case "pdf" -> "PDF";
            case "html", "htm" -> "HTML";
            default -> "FILE";
        };
    }

    public List<ExtractedSection> extractSections(File file) throws IOException {

        String extension = extensionOf(file == null ? null : file.getName());

        return switch (extension) {
            case "epub" -> List.copyOf(
                    epubTextExtractionService.extractSections(file)
            );
            case "pdf" -> pdfTextExtractionService.extractSections(file);
            case "html", "htm" -> htmlTextExtractionService.extractSections(file);
            default -> throw new IllegalArgumentException(
                    "Unsupported book format: " + extension
            );
        };
    }
}
