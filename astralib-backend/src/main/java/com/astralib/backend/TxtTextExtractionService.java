package com.astralib.backend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts readable sections from plain text (.txt) files.
 *
 * Markdown-style headings (# Heading), numbered headings (Chapter 1, Section 1),
 * or paragraph blocks are used to segment the text into meaningful sections.
 */
@Service
public class TxtTextExtractionService {

    private static final Logger logger = LoggerFactory.getLogger(TxtTextExtractionService.class);

    private static final Pattern HEADING_PATTERN = Pattern.compile(
            "^\\s*(?:#{1,6}\\s+|chapter\\s+\\d+|section\\s+\\d+|\\d+\\.\\s+\\p{Lu}).*",
            Pattern.CASE_INSENSITIVE
    );

    private static final int MAX_SECTION_CHARACTERS = 3000;

    public List<ExtractedSection> extractSections(File txtFile) throws IOException {
        List<ExtractedSection> sections = new ArrayList<>();

        if (txtFile == null) {
            return sections;
        }

        if (!txtFile.exists() || !txtFile.isFile()) {
            throw new IllegalArgumentException("TXT file does not exist: " + txtFile.getAbsolutePath());
        }

        byte[] bytes = Files.readAllBytes(txtFile.toPath());
        if (bytes.length == 0) {
            return sections;
        }

        String content = decodeText(bytes);
        if (content.isBlank()) {
            return sections;
        }

        String[] lines = content.split("\r?\n");

        StringBuilder current = new StringBuilder();
        String currentTitle = null;
        int sectionOrder = 0;

        for (String line : lines) {
            String trimmed = line.trim();

            boolean isHeading = !trimmed.isEmpty() && trimmed.length() <= 100 && HEADING_PATTERN.matcher(trimmed).matches();
            boolean sizeExceeded = current.length() >= MAX_SECTION_CHARACTERS;

            if (current.length() > 0 && (isHeading || (sizeExceeded && trimmed.isEmpty()))) {
                sections.add(new SimpleSection(
                        currentTitle != null ? currentTitle : "Section " + (sectionOrder + 1),
                        current.toString().trim(),
                        sectionOrder++
                ));
                current.setLength(0);
                currentTitle = null;
            }

            if (current.length() == 0 && isHeading) {
                currentTitle = cleanHeading(trimmed);
            }

            if (!trimmed.isEmpty() || current.length() > 0) {
                if (current.length() > 0) {
                    current.append("\n");
                }
                current.append(line);
            }
        }

        if (current.length() > 0 && !current.toString().isBlank()) {
            sections.add(new SimpleSection(
                    currentTitle != null ? currentTitle : "Section " + (sectionOrder + 1),
                    current.toString().trim(),
                    sectionOrder
            ));
        }

        logger.info("TXT extraction: {} sections extracted from {}", sections.size(), txtFile.getName());
        return sections;
    }

    private String cleanHeading(String heading) {
        return heading.replaceAll("^#+\\s*", "").trim();
    }

    private String decodeText(byte[] bytes) {
        // Strip UTF-8 BOM if present
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        }

        try {
            return StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        } catch (Exception e) {
            // Fallback to ISO-8859-1 for legacy text
            return new String(bytes, StandardCharsets.ISO_8859_1);
        }
    }
}
