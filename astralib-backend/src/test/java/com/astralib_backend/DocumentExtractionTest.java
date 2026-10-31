package com.astralib_backend;

import com.astralib.backend.DocumentExtractionService;
import com.astralib.backend.ExtractedSection;
import com.astralib.backend.HtmlTextExtractionService;
import com.astralib.backend.PdfTextExtractionService;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The collection now carries EPUB, PDF and HTML editions. Each format must
 * yield real sections through the same contract, otherwise a stored file is
 * reported as readable without anything to search.
 */
class DocumentExtractionTest {

    @TempDir
    Path temp;

    @Test
    void htmlExtractionSplitsOnHeadingsAndDropsSiteChrome() throws Exception {

        String html = """
                <html><body>
                  <nav>Home | Chapters | About</nav>
                  <script>var tracking = 1;</script>
                  <article>
                    <h1>Chapter One</h1>
                    <p>%s</p>
                    <h2>Chapter Two</h2>
                    <p>%s</p>
                  </article>
                  <footer>Copyright notice</footer>
                </body></html>
                """.formatted("First chapter body text. ".repeat(20),
                "Second chapter body text. ".repeat(20));

        Path file = temp.resolve("book.html");
        Files.writeString(file, html, StandardCharsets.UTF_8);

        List<ExtractedSection> sections =
                new HtmlTextExtractionService().extractSections(file.toFile());

        assertEquals(2, sections.size(), "each heading should open a section");
        assertEquals("Chapter One", sections.get(0).getTitle());
        assertTrue(sections.get(0).getContent().contains("First chapter body text."));
        assertEquals("Chapter Two", sections.get(1).getTitle());
        assertTrue(sections.get(1).getContent().contains("Second chapter body text."));

        String everything = sections.toString();

        assertFalse(everything.contains("tracking"), "scripts must not be indexed");
        assertFalse(everything.contains("Home | Chapters"), "nav must not be indexed");
        assertFalse(everything.contains("Copyright notice"), "footer must not be indexed");
    }

    @Test
    void pdfExtractionReadsTheTextLayerAndNamesChapters() throws Exception {

        File pdf = temp.resolve("book.pdf").toFile();

        try (PDDocument document = new PDDocument()) {

            String[][] pages = {
                    {"Chapter 1 Getting Started", "A program is a sequence of instructions."},
                    {"Chapter 2 Variables", "A variable names a place that holds a value."},
            };

            for (String[] page : pages) {
                PDPage pdfPage = new PDPage();
                document.addPage(pdfPage);

                try (PDPageContentStream content =
                             new PDPageContentStream(document, pdfPage)) {
                    content.beginText();
                    content.setFont(
                            new PDType1Font(Standard14Fonts.FontName.HELVETICA),
                            12
                    );
                    content.setLeading(14.5f);
                    content.newLineAtOffset(50, 700);
                    content.showText(page[0]);
                    content.newLine();

                    for (String line : page[1].repeat(6).split("(?<=\\.)")) {
                        content.showText(line.trim());
                        content.newLine();
                    }

                    content.endText();
                }
            }

            document.save(pdf);
        }

        List<ExtractedSection> sections =
                new PdfTextExtractionService().extractSections(pdf);

        assertEquals(2, sections.size(), "one section per chapter heading");
        assertEquals("Chapter 1 Getting Started", sections.get(0).getTitle());
        assertTrue(
                sections.get(0).getContent().contains("sequence of instructions"),
                "real prose must be extracted"
        );
        assertEquals("Chapter 2 Variables", sections.get(1).getTitle());
    }

    @Test
    void dispatcherRecognisesOnlySupportedFormats() {

        DocumentExtractionService service = new DocumentExtractionService(
                new com.astralib.backend.EpubTextExtractionService(),
                new PdfTextExtractionService(),
                new HtmlTextExtractionService()
        );

        assertTrue(service.supports("book.epub", null));
        assertTrue(service.supports("book.pdf", null));
        assertTrue(service.supports("book.html", null));
        assertFalse(service.supports("book.docx", null));
        assertFalse(service.supports(null, null));

        assertEquals("application/pdf", service.formatOf("x.PDF"));
        assertEquals("text/html", service.formatOf("x.htm"));
        assertEquals("EPUB", service.labelOf("x.epub"));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.extractSections(new File("x.docx"))
        );
    }
}
