package com.astralib_backend;

import com.astralib.backend.Author;
import com.astralib.backend.AuthorRepository;
import com.astralib.backend.Book;
import com.astralib.backend.BookImportService;
import com.astralib.backend.BookRepository;
import com.astralib.backend.Category;
import com.astralib.backend.CategoryRepository;
import com.astralib.backend.DigitalResource;
import com.astralib.backend.DigitalResourceRepository;
import com.astralib.backend.EmbeddingService;
import com.astralib.backend.VectorIngestionService;
import com.astralib.backend.VectorRecord;
import com.astralib.backend.VectorRecordRepository;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * End-to-end tests of the book vector pipeline on the isolated H2 database.
 *
 * The embedding provider is replaced with a deterministic stub, so these
 * tests verify resolution, EPUB extraction, chunking, vector persistence
 * and duplicate handling without needing the Python model service.
 */
class BookIngestionPipelineTest extends AbstractIntegrationTest {

    private static final int DIMENSION = 384;

    @Autowired
    private VectorIngestionService vectorIngestionService;

    @Autowired
    private BookImportService bookImportService;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private AuthorRepository authorRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private DigitalResourceRepository digitalResourceRepository;

    @Autowired
    private VectorRecordRepository vectorRecordRepository;

    @Value("${astralib.data-root}")
    private String dataRoot;

    @Value("${astralib.upload-root}")
    private String uploadRoot;

    @MockitoBean
    private EmbeddingService embeddingService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void stubEmbeddings() {
        when(embeddingService.generateEmbedding(anyString()))
                .thenAnswer(invocation -> vector(0.01f));
    }

    // =========================================================
    // FIXTURE HELPERS
    // =========================================================

    private static float[] vector(float seed) {

        float[] values = new float[DIMENSION];

        for (int i = 0; i < DIMENSION; i++) {
            values[i] = seed + (i % 7) * 0.001f;
        }

        return values;
    }

    private Book newBook(String resourceId) {

        Author author = authorRepository.save(
                new Author("Ingestion Author " + resourceId, "bio")
        );

        Category category = categoryRepository.save(
                new Category("Ingestion " + resourceId, "desc")
        );

        Book book = new Book();
        book.setTitle("Ingestion Book " + resourceId);
        book.setResourceId(resourceId);
        book.setAuthor(author);
        book.setCategory(category);
        book.setLanguage("English");
        book.setResourceType("BOOK");

        return bookRepository.save(book);
    }

    private DigitalResource attach(Book book, String storedPath) {

        DigitalResource resource = new DigitalResource();
        resource.setBook(book);
        resource.setFileName("book.epub");
        resource.setFilePath(storedPath);
        resource.setFormat("application/epub+zip");
        resource.setResourceType("BOOK");
        resource.setUploadDate(LocalDateTime.now());

        return digitalResourceRepository.save(resource);
    }

    private Path writeEpub(Path relativeTo, String name) throws Exception {

        Path target = relativeTo.resolve(name);

        Files.createDirectories(target.getParent());
        Files.write(target, epubBytes("Alpha", "Beta", "Gamma"));

        return target;
    }

    /** Minimal but valid EPUB 2 archive with three chapters. */
    private static byte[] epubBytes(String... chapterTitles) throws Exception {

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {

            put(zip, "mimetype", "application/epub+zip");

            put(zip, "META-INF/container.xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                      <rootfiles>
                        <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                      </rootfiles>
                    </container>
                    """);

            StringBuilder manifest = new StringBuilder();
            StringBuilder spine = new StringBuilder();

            for (int i = 0; i < chapterTitles.length; i++) {

                manifest.append(
                        "<item id=\"c" + i + "\" href=\"chapter" + i
                                + ".xhtml\" media-type=\"application/xhtml+xml\"/>"
                );

                spine.append("<itemref idref=\"c" + i + "\"/>");

                put(zip, "OEBPS/chapter" + i + ".xhtml", """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <html xmlns="http://www.w3.org/1999/xhtml">
                          <head><title>%s</title></head>
                          <body>
                            <h1>%s</h1>
                            <p>%s</p>
                          </body>
                        </html>
                        """.formatted(
                        chapterTitles[i],
                        chapterTitles[i],
                        chapterText(chapterTitles[i])
                ));
            }

            put(zip, "OEBPS/content.opf", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="id">
                      <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>Ingestion Fixture</dc:title>
                        <dc:creator>Fixture Author</dc:creator>
                        <dc:identifier id="id">urn:uuid:fixture</dc:identifier>
                        <dc:language>en</dc:language>
                      </metadata>
                      <manifest>
                        %s
                      </manifest>
                      <spine>
                        %s
                      </spine>
                    </package>
                    """.formatted(manifest, spine));
        }

        return bytes.toByteArray();
    }

    private static String chapterText(String title) {

        return ("This is the " + title + " section of the ingestion fixture. ")
                + "It describes knowledge graphs, embeddings and retrieval in enough "
                + "detail that the chunker produces real textbook prose.";
    }

    private static void put(ZipOutputStream zip, String name, String content)
            throws Exception {

        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private List<VectorRecord> vectorsFor(Book book) {

        return vectorRecordRepository.findByDocumentId(book.getResourceId());
    }

    // =========================================================
    // 1. uploads/ PATH
    // =========================================================

    @Test
    void ingestsEpubRegisteredUnderTheUploadRoot() throws Exception {

        Book book = newBook("TEST-UPLOAD-" + System.nanoTime());

        Path uploads = Path.of(uploadRoot);
        writeEpub(uploads, "uploaded-book.epub");

        attach(book, "uploads/uploaded-book.epub");

        Map<String, Object> stats = vectorIngestionService.ingestBookVectors(book.getId());

        assertTrue(
                ((Number) stats.get("vectorsStored")).intValue() > 0,
                "Vectors should be stored from an uploads/ EPUB: " + stats
        );

        List<VectorRecord> vectors = vectorsFor(book);

        assertFalse(vectors.isEmpty());
        assertEquals(book.getResourceId(), vectors.get(0).getDocumentId());

        Set<String> ids = new HashSet<>();

        for (VectorRecord vector : vectors) {

            assertTrue(ids.add(vector.getChunkId()), "Chunk IDs must be unique");

            assertTrue(
                    vector.getChunkId().startsWith(book.getResourceId() + "-CHK-"),
                    "Unexpected chunk id: " + vector.getChunkId()
            );

            assertNotNull(vector.getSection());
            assertFalse(vector.getContent().isBlank());

            float[] embedding = objectMapper.readValue(
                    vector.getEmbeddingJson(),
                    float[].class
            );

            assertEquals(DIMENSION, embedding.length);
        }
    }

    // =========================================================
    // 2. fulltext/ PATH
    // =========================================================

    @Test
    void ingestsEpubRegisteredUnderTheDataRoot() throws Exception {

        Book book = newBook("TEST-FULLTEXT-" + System.nanoTime());

        Path relative = Path.of("fulltext", "books", "fixture", "fixture.epub");

        writeEpub(Path.of(dataRoot), relative.toString());

        attach(book, relative.toString().replace('\\', '/'));

        Map<String, Object> stats = vectorIngestionService.ingestBookVectors(book.getId());

        assertTrue(((Number) stats.get("vectorsStored")).intValue() > 0);
        assertFalse(vectorsFor(book).isEmpty());
    }

    // =========================================================
    // 3. MISSING FILE
    // =========================================================

    @Test
    void missingEpubFailsInsteadOfStoringMetadataVectors() {

        Book book = newBook("TEST-MISSING-" + System.nanoTime());

        attach(book, "uploads/does-not-exist.epub");

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> vectorIngestionService.ingestBookVectors(book.getId())
        );

        assertTrue(
                error.getMessage().contains("could not be found"),
                "Unexpected message: " + error.getMessage()
        );

        assertTrue(
                vectorsFor(book).isEmpty(),
                "A missing EPUB must not leave metadata-only vectors behind"
        );
    }

    // =========================================================
    // 4. INVALID EPUB
    // =========================================================

    @Test
    void unreadableEpubFailsClearly() throws Exception {

        Book book = newBook("TEST-BROKEN-" + System.nanoTime());

        Path uploads = Path.of(uploadRoot);
        Files.createDirectories(uploads);
        Files.write(
                uploads.resolve("broken.epub"),
                "this is not a zip archive".getBytes(StandardCharsets.UTF_8)
        );

        attach(book, "uploads/broken.epub");

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> vectorIngestionService.ingestBookVectors(book.getId())
        );

        assertNotNull(error.getMessage());
        assertTrue(vectorsFor(book).isEmpty());
    }

    // =========================================================
    // 5. IDEMPOTENT RE-INGESTION
    // =========================================================

    @Test
    void repeatIngestionDoesNotCreateDuplicateChunks() throws Exception {

        Book book = newBook("TEST-REPEAT-" + System.nanoTime());

        writeEpub(Path.of(uploadRoot), "repeat.epub");
        attach(book, "uploads/repeat.epub");

        Map<String, Object> first = vectorIngestionService.ingestBookVectors(book.getId());

        int stored = ((Number) first.get("vectorsStored")).intValue();
        long countAfterFirst = vectorsFor(book).size();

        assertEquals(stored, countAfterFirst);

        Map<String, Object> second = vectorIngestionService.ingestBookVectors(book.getId());

        assertEquals(0, ((Number) second.get("vectorsStored")).intValue());
        assertEquals(stored, ((Number) second.get("duplicatesSkipped")).intValue());
        assertEquals(
                countAfterFirst,
                vectorsFor(book).size(),
                "Re-ingestion must not duplicate chunks"
        );
    }

    // =========================================================
    // 6. PATH TRAVERSAL
    // =========================================================

    @Test
    void rejectsPathsThatEscapeTheStorageRoots() {

        Book book = newBook("TEST-TRAVERSAL-" + System.nanoTime());

        attach(book, "uploads/../../escaped.epub");

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> vectorIngestionService.ingestBookVectors(book.getId())
        );

        assertTrue(
                error.getMessage().contains("outside its storage root"),
                "Unexpected message: " + error.getMessage()
        );
    }

    // =========================================================
    // 7. OVERLONG SECTION TITLES
    // =========================================================

    @Test
    void truncatesSectionTitlesThatDoNotFitTheColumn() throws Exception {

        Book book = newBook("TEST-LONGTITLE-" + System.nanoTime());

        String longTitle = "Chapter " + "Operating Systems ".repeat(30);

        Path uploads = Path.of(uploadRoot);
        Files.createDirectories(uploads);
        Files.write(
                uploads.resolve("long-title.epub"),
                epubBytes(longTitle)
        );

        attach(book, "uploads/long-title.epub");

        Map<String, Object> stats = vectorIngestionService.ingestBookVectors(book.getId());

        assertTrue(
                ((Number) stats.get("vectorsStored")).intValue() > 0,
                "An overlong chapter heading must not abort ingestion: " + stats
        );

        for (VectorRecord vector : vectorsFor(book)) {
            assertTrue(
                    vector.getSection().length() <= 255,
                    "Section must fit vector_records.section"
            );
        }
    }


    // =========================================================
    // 8. RE-IMPORT MUST NOT ERASE METADATA
    // =========================================================

    @Test
    void reimportKeepsMetadataThatWasNotSuppliedAgain() throws Exception {

        byte[] epub = epubBytes("Alpha");
        String resourceId = "TEST-META-" + System.nanoTime();

        MultipartFile first = new MockMultipartFile(
                "file", "meta.epub", "application/epub+zip", epub
        );

        Map<String, Object> created = bookImportService.importEpub(
                first,
                resourceId,
                "Metadata Book",
                "Original Author",
                "Original Category",
                "Original description",
                2015,
                "Original Publisher",
                "English",
                "978-0-00-000001-1",
                null,
                "Alpha, Beta"
        );

        Long bookId = (Long) created.get("bookId");

        MultipartFile second = new MockMultipartFile(
                "file", "meta.epub", "application/epub+zip", epub
        );

        Map<String, Object> again = bookImportService.importEpub(
                second,
                resourceId,
                "Metadata Book",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertEquals(bookId, again.get("bookId"), "Re-import must update the same book");
        assertFalse((Boolean) again.get("bookCreated"));

        Book stored = bookRepository.findById(bookId).orElseThrow();

        assertEquals(Integer.valueOf(2015), stored.getPublicationYear());
        assertEquals("Original Publisher", stored.getPublisher());
        assertEquals("Original description", stored.getDescription());
        assertEquals("Original Author", stored.getAuthor().getName());
        assertEquals("Original Category", stored.getCategory().getName());
        assertEquals("978-0-00-000001-1", stored.getIsbn());
    }


    // =========================================================
    // 8b. A CATALOGUED ENTRY GAINS ITS FILE
    // =========================================================

    @Test
    void importingAFileAttachesItToACataloguedEntry() throws Exception {

        Author author = authorRepository.save(new Author("Adoption Author", null));
        Category category = categoryRepository.save(new Category("Adoption Category", null));

        Book entry = new Book();
        entry.setTitle("Adoption Target");
        entry.setAuthor(author);
        entry.setCategory(category);
        entry.setLanguage("English");
        entry.setCatalogueEntry(Boolean.TRUE);
        entry = bookRepository.save(entry);

        Long entryId = entry.getId();
        long countBefore = bookRepository.count();

        MultipartFile file = new MockMultipartFile(
                "file", "adoption.epub", "application/epub+zip", epubBytes("Alpha")
        );

        Map<String, Object> result = bookImportService.importEpub(
                file,
                "TEST-ADOPT-" + System.nanoTime(),
                "Adoption Target",
                "Adoption Author",
                "Adoption Category",
                "Catalogued first, file later",
                2020,
                "Adoption Publisher",
                "English",
                null,
                null,
                null
        );

        assertEquals(entryId, result.get("bookId"),
                "The file must attach to the catalogued entry, not create a second book");
        assertEquals(countBefore, bookRepository.count(),
                "No duplicate catalogue record may be created");

        boolean ownsFile = digitalResourceRepository.findAll().stream()
                .anyMatch(resource -> resource.getBook() != null
                        && entryId.equals(resource.getBook().getId())
                        && resource.getFilePath() != null);

        assertTrue(
                ownsFile,
                "The adopted record must own the uploaded file"
        );
    }


    // =========================================================
    // 9. PARTIAL FAILURE
    // =========================================================

    @Test
    void reportsChunksThatFailedToEmbed() throws Exception {

        Book book = newBook("TEST-PARTIAL-" + System.nanoTime());

        writeEpub(Path.of(uploadRoot), "partial.epub");
        attach(book, "uploads/partial.epub");

        when(embeddingService.generateEmbedding(anyString()))
                .thenThrow(new IllegalStateException("simulated embedding outage"))
                .thenAnswer(invocation -> vector(0.02f));

        Map<String, Object> stats = vectorIngestionService.ingestBookVectors(book.getId());

        assertTrue(
                ((Number) stats.get("failedRecords")).intValue() > 0,
                "Failed chunks must be reported: " + stats
        );

        assertTrue(
                ((Number) stats.get("vectorsStored")).intValue() > 0,
                "Successfully embedded chunks should still be stored: " + stats
        );

        assertEquals(
                ((Number) stats.get("vectorsStored")).intValue(),
                vectorsFor(book).size(),
                "Stored count must match persisted rows"
        );
    }
}
