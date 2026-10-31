package com.astralib.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * Applies the curated collection manifest.
 *
 * <p>The manifest (catalogue/collection.json) lists every book of the
 * curated library, its source and licence, and the pathways it belongs
 * to. Applying it is idempotent:
 *
 * <ul>
 *   <li>a book already in the catalogue is updated in place, never
 *       duplicated, and its vectors are left alone;</li>
 *   <li>a missing book is imported through the existing
 *       {@link BookImportService}, which saves the EPUB, registers the
 *       resource and starts the existing per-book ingestion job;</li>
 *   <li>pathway memberships, levels and prerequisites are upserted.</li>
 * </ul>
 *
 * This is an administrative curation tool, not a second ingestion
 * pipeline.
 */
@Service
public class CatalogueSeedService {

    private static final Logger logger =
            LoggerFactory.getLogger(CatalogueSeedService.class);

    @Value("${astralib.data-root:../data}")
    private String dataRoot;

    @Value("${astralib.catalogue-manifest:../catalogue/collection.json}")
    private String manifestPath;

    @Autowired
    private BookImportService bookImportService;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private AuthorRepository authorRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private PathwayRepository pathwayRepository;

    @Autowired
    private PathwayBookRepository pathwayBookRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // =========================================================
    // APPLY
    // =========================================================

    public Map<String, Object> applyManifest() throws IOException {

        Path manifest = resolveManifestPath();

        if (!Files.isRegularFile(manifest)) {
            throw new IllegalStateException(
                    "Catalogue manifest not found: " + manifest
            );
        }

        JsonNode root = objectMapper.readTree(manifest.toFile());

        Map<String, Book> booksByRef = new LinkedHashMap<>();
        List<Map<String, Object>> bookReport = new ArrayList<>();

        int created = 0;
        int catalogued = 0;
        int updated = 0;
        int missingFiles = 0;
        int attached = 0;
        int failed = 0;

        for (JsonNode node : root.path("books")) {

            String ref = text(node, "resourceId");
            String title = text(node, "title");

            if (ref == null || title == null) {
                Map<String, Object> invalid = new LinkedHashMap<>();
                invalid.put("title", title);
                invalid.put("action", "INVALID_ENTRY");
                invalid.put("error", "resourceId and title are required");
                bookReport.add(invalid);
                failed++;
                continue;
            }

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("resourceId", ref);
            entry.put("title", title);

            /*
             * A curated entry we may describe but not distribute: a
             * commercial title with no licensed file. It is catalogued,
             * levelled and searchable as metadata, and the catalogue
             * marks it as not available for reading. It never receives
             * vectors, so it is never reported as indexed.
             */
            boolean metadataOnly = "METADATA_ONLY"
                    .equalsIgnoreCase(String.valueOf(text(node, "availability")));

            /*
             * A book that is already imported and indexed elsewhere in the
             * catalogue (think Python, Eloquent JavaScript) joins pathways
             * without being re-imported or having its metadata rewritten.
             * Only the pathway membership refers to it, so the existing
             * record, resource and vectors stay exactly as they are.
             */
            boolean attachOnly = node.path("attachOnly").asBoolean(false);

            if (attachOnly) {

                Book existing = bookRepository.findByResourceId(ref).orElse(null);

                entry.put(
                        "action",
                        existing == null ? "MISSING_EXISTING_BOOK" : "ATTACHED"
                );

                if (existing == null) {
                    failed++;
                } else {
                    booksByRef.put(ref, existing);
                    entry.put("bookId", existing.getId());
                    attached++;
                }

                bookReport.add(entry);
                continue;
            }

            try {
                Book book = findExistingBook(node);

                if (book == null && metadataOnly) {

                    book = new Book();
                    applyBookMetadata(book, node);

                    // No file is held, so no download format is claimed.
                    book.setResourceType(null);
                    book.setCatalogueEntry(Boolean.TRUE);
                    book = bookRepository.save(book);

                    entry.put("action", "CATALOGUED");
                    entry.put("bookId", book.getId());
                    catalogued++;
                    bookReport.add(entry);

                    booksByRef.put(ref, book);
                    continue;
                }

                if (book == null) {

                    Path file = resolveBookFile(text(node, "file"));

                    if (file == null) {
                        entry.put("action", "MISSING_FILE");
                        missingFiles++;
                        bookReport.add(entry);
                        continue;
                    }

                    Map<String, Object> result = bookImportService.importEpub(
                            new FileMultipartFile(file),
                            ref,
                            title,
                            text(node, "author"),
                            text(node, "category"),
                            text(node, "description"),
                            integer(node, "publicationYear"),
                            text(node, "publisher"),
                            text(node, "language"),
                            null,
                            null,
                            joinList(node.path("subjects"))
                    );

                    entry.put("action", "IMPORTED");
                    entry.put("jobId", result.get("ingestionJobId"));

                    book = bookRepository.findByResourceId(ref).orElse(null);

                    created++;

                } else {
                    entry.put("action", "UPDATED");
                    updated++;
                }

                if (book == null) {
                    entry.put("action", "FAILED");
                    entry.put("error", "Book could not be read back after import");
                    failed++;
                    bookReport.add(entry);
                    continue;
                }

                applyBookMetadata(book, node);

                if (metadataOnly) {
                    book.setCatalogueEntry(Boolean.TRUE);
                }

                book = bookRepository.save(book);

                booksByRef.put(ref, book);

                entry.put("bookId", book.getId());

            } catch (Exception e) {
                logger.warn(
                        "Catalogue seed failed for {}: {}",
                        title,
                        e.getMessage()
                );
                entry.put("action", "FAILED");
                entry.put("error", e.getMessage());
                failed++;
            }

            bookReport.add(entry);
        }

        List<Map<String, Object>> pathwayReport = new ArrayList<>();

        for (JsonNode node : root.path("pathways")) {
            pathwayReport.add(applyPathway(node, booksByRef));
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("manifest", manifest.normalize().toString());
        summary.put("booksInManifest", root.path("books").size());
        summary.put("booksImported", created);
        summary.put("booksCataloguedOnly", catalogued);
        summary.put("booksAttached", attached);
        summary.put("booksUpdated", updated);
        summary.put("booksMissingFile", missingFiles);
        summary.put("booksFailed", failed);
        summary.put("pathwaysApplied", pathwayReport.size());
        summary.put("books", bookReport);
        summary.put("pathways", pathwayReport);

        return summary;
    }

    // =========================================================
    // BOOKS
    // =========================================================

    private Book findExistingBook(JsonNode node) {

        String ref = text(node, "resourceId");

        Optional<Book> byResource = bookRepository.findByResourceId(ref);

        if (byResource.isPresent()) {
            return byResource.get();
        }

        Optional<Book> byIsbn = Optional.empty();

        String isbn = text(node, "isbn");

        if (isbn != null) {
            byIsbn = bookRepository.findByIsbn(isbn);
        }

        if (byIsbn.isPresent()) {
            return byIsbn.get();
        }

        /*
         * Fall back to title + author so a pre-existing catalogue
         * record is adopted instead of duplicated. Only an exact
         * normalised title match is accepted.
         */
        String wantedTitle = normalize(text(node, "title"));
        String wantedAuthor = normalize(text(node, "author"));

        for (Book candidate : bookRepository.findAll()) {

            if (!normalize(candidate.getTitle()).equals(wantedTitle)) {
                continue;
            }

            /*
             * Never adopt a record that already has its own resource:
             * that would point two books at one EPUB.
             */
            if (candidate.getResourceId() != null
                    && !candidate.getResourceId().isBlank()) {
                continue;
            }

            if (wantedAuthor == null) {
                return candidate;
            }

            String candidateAuthor = candidate.getAuthor() == null
                    ? null
                    : normalize(candidate.getAuthor().getName());

            if (candidateAuthor != null && candidateAuthor.contains(wantedAuthor)) {
                return candidate;
            }
        }

        return null;
    }

    private void applyBookMetadata(Book book, JsonNode node) throws Exception {

        book.setTitle(text(node, "title"));
        book.setResourceId(text(node, "resourceId"));

        String description = text(node, "description");
        if (description != null) {
            book.setDescription(description);
        }

        String isbn = text(node, "isbn");
        if (isbn != null && (book.getIsbn() == null || book.getIsbn().isBlank())) {
            book.setIsbn(isbn);
        }

        Integer year = integer(node, "publicationYear");
        if (year != null) {
            book.setPublicationYear(year);
        }

        String publisher = text(node, "publisher");
        if (publisher != null) {
            book.setPublisher(publisher);
        }

        String language = text(node, "language");
        if (language != null) {
            book.setLanguage(language);
        }

        String edition = text(node, "edition");
        if (edition != null) {
            book.setEdition(edition);
        }

        String sourceUrl = text(node, "sourceUrl");
        if (sourceUrl != null) {
            book.setSourceUrl(sourceUrl);
        }

        String licence = text(node, "licence");
        if (licence != null) {
            book.setLicence(licence);
        }

        String coverImage = text(node, "coverImage");
        if (coverImage != null) {
            book.setCoverImage(coverImage);
        }

        List<String> subjects = toList(node.path("subjects"));
        if (!subjects.isEmpty()) {
            book.setSubjects(subjects);
        }

        List<String> genres = toList(node.path("genres"));
        if (!genres.isEmpty()) {
            book.setGenres(genres);
        }

        String authorName = text(node, "author");
        if (authorName != null) {
            book.setAuthor(findOrCreateAuthor(authorName));
        }

        String categoryName = text(node, "category");
        if (categoryName != null) {
            book.setCategory(findOrCreateCategory(categoryName));
        }

        if (book.getResourceType() == null || book.getResourceType().isBlank()) {
            book.setResourceType("EPUB");
        }
    }

    private Author findOrCreateAuthor(String name) {

        return authorRepository.findAll().stream()
                .filter(a -> a.getName() != null
                        && a.getName().equalsIgnoreCase(name.trim()))
                .findFirst()
                .orElseGet(() -> authorRepository.save(new Author(name.trim(), null)));
    }

    private Category findOrCreateCategory(String name) {

        return categoryRepository.findAll().stream()
                .filter(c -> c.getName() != null
                        && c.getName().equalsIgnoreCase(name.trim()))
                .findFirst()
                .orElseGet(() -> categoryRepository.save(new Category(name.trim(), null)));
    }

    // =========================================================
    // PATHWAYS
    // =========================================================

    private Map<String, Object> applyPathway(
            JsonNode node,
            Map<String, Book> booksByRef
    ) {

        String slug = text(node, "slug");
        String name = text(node, "name");

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("slug", slug);
        report.put("name", name);

        if (slug == null || name == null) {
            report.put("status", "INVALID");
            return report;
        }

        Pathway pathway = pathwayRepository.findBySlug(slug)
                .orElseGet(Pathway::new);

        pathway.setSlug(slug);
        pathway.setName(name);
        pathway.setType(
                Pathway.TYPE_LEISURE.equalsIgnoreCase(text(node, "type"))
                        ? Pathway.TYPE_LEISURE
                        : Pathway.TYPE_LEARNING
        );
        pathway.setSubject(text(node, "subject"));
        pathway.setAudience(text(node, "audience"));
        pathway.setDescription(text(node, "description"));

        pathway = pathwayRepository.save(pathway);

        /*
         * Replace the membership rows so re-applying a corrected
         * manifest never leaves stale entries. Book records and
         * vectors are untouched.
         */
        pathwayBookRepository.deleteAll(
                pathwayBookRepository.findByPathway_IdOrderByPositionAsc(pathway.getId())
        );

        int added = 0;
        int position = 0;
        List<String> skipped = new ArrayList<>();

        for (JsonNode entry : node.path("books")) {

            String ref = text(entry, "resourceId");
            Book book = ref == null ? null : booksByRef.get(ref);

            if (book == null) {
                skipped.add(ref == null ? "?" : ref);
                continue;
            }

            PathwayBook membership = new PathwayBook();
            membership.setPathway(pathway);
            membership.setBook(book);
            membership.setPosition(
                    integer(entry, "position") != null
                            ? integer(entry, "position")
                            : ++position
            );
            membership.setLevel(text(entry, "level"));
            membership.setRationale(text(entry, "rationale"));
            membership.setLearningOutcome(text(entry, "learningOutcome"));

            String prerequisiteRef = text(entry, "prerequisite");

            if (prerequisiteRef != null) {
                Book prerequisite = booksByRef.get(prerequisiteRef);

                if (prerequisite != null && !prerequisite.getId().equals(book.getId())) {
                    membership.setPrerequisiteBook(prerequisite);
                }
            }

            pathwayBookRepository.save(membership);
            added++;
        }

        report.put("status", "APPLIED");
        report.put("booksApplied", added);
        report.put("skipped", skipped);
        report.put("pathwayId", pathway.getId());

        return report;
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private Path resolveManifestPath() {
        return resolveAgainstProject(manifestPath);
    }

    /**
     * Resolves a manifest-relative path against the data root, then the
     * backend working directory and its parent. No absolute developer
     * path is ever hardcoded.
     */
    private Path resolveBookFile(String file) {

        if (file == null) {
            return null;
        }

        List<Path> candidates = List.of(
                getDataRootPath().getParent() == null
                        ? getDataRootPath().resolve(file)
                        : getDataRootPath().getParent().resolve(file),
                Paths.get(file),
                resolveAgainstProject(file)
        );

        for (Path candidate : candidates) {
            Path normalized = candidate.normalize();
            if (Files.isRegularFile(normalized)) {
                return normalized;
            }
        }

        return null;
    }

    private Path resolveAgainstProject(String value) {
        Path direct = Paths.get(value);

        if (Files.exists(direct)) {
            return direct.normalize();
        }

        return direct.toAbsolutePath().normalize();
    }

    private Path getDataRootPath() {
        Path path = Paths.get(dataRoot);
        return path.isAbsolute() ? path : path.toAbsolutePath().normalize();
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String raw = value.asText();
        return raw == null || raw.isBlank() ? null : raw.trim();
    }

    private static Integer integer(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.asInt() : null;
    }

    private static List<String> toList(JsonNode array) {

        List<String> values = new ArrayList<>();

        if (array != null && array.isArray()) {
            for (JsonNode item : array) {
                String value = item.asText();
                if (value != null && !value.isBlank()) {
                    values.add(value.trim());
                }
            }
        }

        return values;
    }

    private static String joinList(JsonNode array) {
        List<String> values = toList(array);
        return values.isEmpty() ? null : String.join(", ", values);
    }

    /**
     * Minimal MultipartFile adapter so an already-downloaded, licensed
     * EPUB can go through the same import path as a browser upload.
     */
    private static final class FileMultipartFile implements MultipartFile {

        private final Path path;

        private FileMultipartFile(Path path) {
            this.path = path;
        }

        @Override
        public String getName() {
            return "file";
        }

        @Override
        public String getOriginalFilename() {
            return path.getFileName().toString();
        }

        @Override
        public String getContentType() {
            return "application/epub+zip";
        }

        @Override
        public boolean isEmpty() {
            try {
                return Files.size(path) == 0;
            } catch (IOException e) {
                return true;
            }
        }

        @Override
        public long getSize() {
            try {
                return Files.size(path);
            } catch (IOException e) {
                return 0;
            }
        }

        @Override
        public byte[] getBytes() throws IOException {
            return Files.readAllBytes(path);
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return Files.newInputStream(path);
        }

        @Override
        public void transferTo(File dest) throws IOException, IllegalStateException {
            Files.copy(path, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
