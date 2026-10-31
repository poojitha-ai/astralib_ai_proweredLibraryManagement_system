package com.astralib.backend;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class BookImportService {

    // =========================================================
    // PORTABLE DATA ROOT
    // =========================================================

    @Value("${astralib.data-root:../data}")
    private String dataRoot;


    // =========================================================
    // REPOSITORIES
    // =========================================================

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private AuthorRepository authorRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private DigitalResourceRepository digitalResourceRepository;

    @Autowired
    private BookIngestionJobService bookIngestionJobService;

    @Autowired
    private DocumentExtractionService documentExtractionService;


    // =========================================================
    // IMPORT EPUB BOOK
    // =========================================================

    public Map<String, Object> importEpub(
            MultipartFile bookFile,
            String resourceId,
            String title,
            String authorName,
            String categoryName,
            String description,
            Integer publicationYear,
            String publisher,
            String language,
            String isbn,
            String coverImage,
            String subjectsText
    ) throws IOException {

        // =====================================================
        // VALIDATION
        // =====================================================

        if (bookFile == null || bookFile.isEmpty()) {
            throw new IllegalArgumentException(
                    "A book file (EPUB, PDF or HTML) is required."
            );
        }

        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException(
                    "Book title is required."
            );
        }

        if (resourceId == null || resourceId.isBlank()) {
            throw new IllegalArgumentException(
                    "resourceId is required."
            );
        }

        String originalFileName =
                bookFile.getOriginalFilename();

        /*
         * EPUB, PDF and HTML all feed the same extraction pipeline.
         * Anything else is rejected rather than stored unindexable.
         */
        String extension =
                documentExtractionService.extensionOf(
                        originalFileName
                );

        if (
                !documentExtractionService.supports(
                        originalFileName,
                        bookFile.getContentType()
                )
        ) {

            throw new IllegalArgumentException(
                    "Only EPUB, PDF and HTML files are supported."
            );
        }


        resourceId = resourceId.trim();

        title = title.trim();

        isbn = cleanNullable(isbn);


        // =====================================================
        // FIND EXISTING BOOK
        //
        // resource_id is our main stable identifier.
        // =====================================================

        Optional<Book> existingByResource =
                bookRepository.findByResourceId(
                        resourceId
                );

        Book book;

        boolean bookCreated = false;


        if (existingByResource.isPresent()) {

            book =
                    existingByResource.get();

        } else {

            // -------------------------------------------------
            // Secondary duplicate check using ISBN
            // -------------------------------------------------

            Optional<Book> existingByIsbn =
                    Optional.empty();

            if (isbn != null) {

                existingByIsbn =
                        bookRepository.findByIsbn(
                                isbn
                        );
            }


            if (existingByIsbn.isPresent()) {

                book =
                        existingByIsbn.get();


                if (
                        book.getResourceId() != null
                                &&
                        !book.getResourceId().isBlank()
                                &&
                        !book.getResourceId()
                                .equalsIgnoreCase(
                                        resourceId
                                )
                ) {

                    throw new IllegalStateException(
                            "ISBN already belongs to another resource: "
                                    + book.getResourceId()
                    );
                }


                book.setResourceId(
                        resourceId
                );

            } else {

                /*
                 * Third fallback: a curated catalogue entry for the same
                 * title that has no file yet. Uploading the licensed EPUB
                 * for such a book must attach the file to that record
                 * instead of creating a second catalogue entry for it.
                 */
                Book adoptable =
                        findCataloguedEntryWithoutFile(
                                title,
                                authorName
                        );

                if (adoptable != null) {

                    book = adoptable;

                    book.setResourceId(
                            resourceId
                    );

                } else {

                    book =
                            new Book();

                    book.setResourceId(
                            resourceId
                    );

                    bookCreated = true;
                }
            }
        }


        // =====================================================
        // AUTHOR
        // =====================================================

        /*
         * On re-import, a blank author/category means "keep the existing
         * value" rather than "replace it with Unknown Author / General".
         */
        Author author =
                isBlank(authorName)
                        &&
                book.getAuthor() != null
                        ? book.getAuthor()
                        : findOrCreateAuthor(authorName);


        // =====================================================
        // CATEGORY
        // =====================================================

        Category category =
                isBlank(categoryName)
                        &&
                book.getCategory() != null
                        ? book.getCategory()
                        : findOrCreateCategory(categoryName);


        // =====================================================
        // UPDATE BOOK METADATA
        // =====================================================

        book.setTitle(
                title
        );

        if (!isBlank(description)) {
            book.setDescription(description.trim());
        }

        if (isbn != null) {
            book.setIsbn(isbn);
        }

        if (publicationYear != null) {
            book.setPublicationYear(publicationYear);
        }

        if (!isBlank(publisher)) {
            book.setPublisher(publisher.trim());
        }

        if (!isBlank(language)) {
            book.setLanguage(language.trim());
        } else if (isBlank(book.getLanguage())) {
            book.setLanguage("English");
        }

        if (!isBlank(coverImage)) {
            book.setCoverImage(coverImage.trim());
        }

        if (!isBlank(subjectsText)) {
            book.setSubjects(
                    parseSubjects(
                            subjectsText
                    )
            );
        }

        book.setResourceType(
                "EPUB"
        );

        book.setAuthor(
                author
        );

        book.setCategory(
                category
        );


        /*
         * IMPORTANT:
         *
         * Do not place the EPUB's entire contents inside
         * books.text_content.
         *
         * VectorIngestionService already gives priority to
         * the real EPUB file and extracts it chapter-by-chapter.
         */
        book.setTextContent(
                null
        );


        book =
                bookRepository.save(
                        book
                );


        // =====================================================
        // PORTABLE EPUB DIRECTORY
        //
        // data/fulltext/books/<slug>/
        // =====================================================

        String slug =
                createSlug(
                        title,
                        resourceId
                );


        /*
         * "htm" is stored as "html" so the extension alone always
         * identifies the extractor.
         */
        String storedExtension =
                "htm".equals(extension)
                        ? "html"
                        : extension;


        Path bookDirectory =
                getDataRootPath()
                        .resolve(
                                "fulltext"
                        )
                        .resolve(
                                "books"
                        )
                        .resolve(
                                slug
                        )
                        .normalize();


        Files.createDirectories(
                bookDirectory
        );


        // =====================================================
        // WRITE EPUB
        // =====================================================

        String storedFileName =
                slug + "." + storedExtension;


        Path destination =
                bookDirectory
                        .resolve(
                                storedFileName
                        )
                        .normalize();


        /*
         * Protection against accidentally escaping data-root.
         */
        if (
                !destination.startsWith(
                        getDataRootPath()
                )
        ) {

            throw new IllegalStateException(
                    "Invalid book destination path."
            );
        }


        /*
         * Skip the copy when the identical file is already stored.
         *
         * On Windows the ingestion thread keeps the EPUB open while it
         * extracts chapters, and replacing a file that is being read
         * fails with a sharing violation. Re-importing the same book
         * therefore used to fail with HTTP 500 while indexing was in
         * progress. Identical size means the same uploaded file.
         */
        boolean alreadyStored =
                Files.isRegularFile(
                        destination
                )
                        &&
                Files.size(
                        destination
                ) == bookFile.getSize();


        if (!alreadyStored) {

            Files.copy(
                    bookFile.getInputStream(),
                    destination,
                    StandardCopyOption.REPLACE_EXISTING
            );
        }


        // =====================================================
        // PORTABLE DB PATH
        //
        // NEVER store:
        //
        // C:\Users\DELL\Desktop\...
        //
        // Store:
        //
        // fulltext/books/pride_and_prejudice/
        // pride_and_prejudice.epub
        // =====================================================

        String portablePath =
                "fulltext/books/"
                        + slug
                        + "/"
                        + storedFileName;


        // =====================================================
        // DIGITAL RESOURCE
        // =====================================================

        List<DigitalResource> existingResources =
                digitalResourceRepository
                        .findByBook_Id(
                                book.getId()
                        );


        DigitalResource bookResource =
                null;


        for (
                DigitalResource resource :
                existingResources
        ) {

            if (resource == null) {
                continue;
            }


            /*
             * Re-importing the same edition updates its own resource row.
             * A different format of the same book (PDF after EPUB, say)
             * gets its own row instead of replacing one we still hold.
             */
            if (
                    storedExtension.equals(
                            documentExtractionService.extensionOf(
                                    resource.getFileName()
                            )
                    )
            ) {

                bookResource =
                        resource;

                break;
            }
        }


        boolean resourceCreated =
                false;


        if (bookResource == null) {

            bookResource =
                    new DigitalResource();

            resourceCreated =
                    true;
        }


        bookResource.setBook(
                book
        );

        bookResource.setResourceType(
                "BOOK"
        );

        bookResource.setFileName(
                storedFileName
        );

        bookResource.setFilePath(
                portablePath
        );

        bookResource.setFormat(
                documentExtractionService.formatOf(
                        storedFileName
                )
        );


        bookResource =
                digitalResourceRepository
                        .save(
                                bookResource
                        );


        // =====================================================
        // AUTOMATIC EMBEDDING
        //
        // The existing per-book ingestion is started in the
        // background once the book and its file are safely saved.
        // It never deletes existing vectors and never runs the
        // destructive full re-ingestion.
        // =====================================================

        Map<String, Object> ingestion =
                bookIngestionJobService.submit(
                        book.getId(),
                        book.getTitle()
                );


        // =====================================================
        // RESULT
        // =====================================================

        Map<String, Object> result =
                new LinkedHashMap<>();


        result.put(
                "success",
                true
        );

        result.put(
                "bookCreated",
                bookCreated
        );

        result.put(
                "digitalResourceCreated",
                resourceCreated
        );

        result.put(
                "bookId",
                book.getId()
        );

        result.put(
                "resourceId",
                book.getResourceId()
        );

        result.put(
                "title",
                book.getTitle()
        );

        result.put(
                "author",
                author != null
                        ? author.getName()
                        : null
        );

        result.put(
                "category",
                category != null
                        ? category.getName()
                        : null
        );

        result.put(
                "digitalResourceId",
                bookResource.getId()
        );

        result.put(
                "fileName",
                bookResource.getFileName()
        );

        result.put(
                "filePath",
                bookResource.getFilePath()
        );

        result.put(
                "absoluteFilePath",
                destination.toAbsolutePath()
                        .normalize()
                        .toString()
        );

        result.put(
                "ingestion",
                ingestion
        );


        result.put(
                "ingestionJobId",
                ingestion.get("jobId")
        );


        result.put(
                "nextStep",
                "Embedding started automatically. Poll "
                        + "/api/admin/books/import-status/"
                        + ingestion.get("jobId")
                        + " for progress."
        );


        return result;
    }


    // =========================================================
    // AUTHOR
    // =========================================================

    private Author findOrCreateAuthor(
            String authorName
    ) {

        String safeName =
                authorName == null
                        ||
                authorName.isBlank()
                        ?
                        "Unknown Author"
                        :
                        authorName.trim();


        for (
                Author author :
                authorRepository.findAll()
        ) {

            if (
                    author.getName() != null
                            &&
                    author.getName()
                            .trim()
                            .equalsIgnoreCase(
                                    safeName
                            )
            ) {

                return author;
            }
        }


        Author author =
                new Author(
                        safeName,
                        null
                );


        return authorRepository.save(
                author
        );
    }


    // =========================================================
    // CATEGORY
    // =========================================================

    private Category findOrCreateCategory(
            String categoryName
    ) {

        String safeName =
                categoryName == null
                        ||
                categoryName.isBlank()
                        ?
                        "General"
                        :
                        categoryName.trim();


        for (
                Category category :
                categoryRepository.findAll()
        ) {

            if (
                    category.getName() != null
                            &&
                    category.getName()
                            .trim()
                            .equalsIgnoreCase(
                                    safeName
                            )
            ) {

                return category;
            }
        }


        Category category =
                new Category(
                        safeName,
                        "Category for imported EPUB books"
                );


        return categoryRepository.save(
                category
        );
    }


    // =========================================================
    // SUBJECTS
    //
    // Input:
    //
    // Classic Literature, Romance, England
    // =========================================================

    private List<String> parseSubjects(
            String subjectsText
    ) {

        List<String> subjects =
                new ArrayList<>();


        if (
                subjectsText == null
                        ||
                subjectsText.isBlank()
        ) {

            return subjects;
        }


        Arrays.stream(
                        subjectsText.split(",")
                )
                .map(String::trim)
                .filter(
                        value ->
                                !value.isBlank()
                )
                .distinct()
                .forEach(
                        subjects::add
                );


        return subjects;
    }


    // =========================================================
    // SLUG
    // =========================================================

    private String createSlug(
            String title,
            String resourceId
    ) {

        String slug =
                title
                        .toLowerCase(
                                Locale.ROOT
                        )
                        .replaceAll(
                                "[^a-z0-9]+",
                                "_"
                        )
                        .replaceAll(
                                "^_+|_+$",
                                ""
                        );


        if (slug.isBlank()) {

            slug =
                    resourceId
                            .toLowerCase(
                                    Locale.ROOT
                            )
                            .replaceAll(
                                    "[^a-z0-9]+",
                                    "_"
                            )
                            .replaceAll(
                                    "^_+|_+$",
                                    ""
                            );
        }


        if (slug.isBlank()) {

            slug =
                    "book";
        }


        return slug;
    }


    // =========================================================
    // PORTABLE DATA ROOT
    // =========================================================

    private Path getDataRootPath() {

        return Paths.get(
                        dataRoot
                )
                .toAbsolutePath()
                .normalize();
    }


    // =========================================================
    // NULLABLE STRING CLEANUP
    // =========================================================

    private static boolean isBlank(
            String value
    ) {

        return value == null || value.isBlank();
    }

    /**
     * A metadata-only catalogue entry with the same title (and, when one
     * is supplied, the same author) and no stored file. The query only
     * returns records without a resource, so two books can never end up
     * sharing one EPUB.
     */
    private Book findCataloguedEntryWithoutFile(
            String title,
            String authorName
    ) {

        if (isBlank(title)) {
            return null;
        }

        String wantedTitle =
                normalizeTitle(title);

        for (Book candidate : bookRepository.findWithoutStoredResource()) {

            if (!normalizeTitle(candidate.getTitle()).equals(wantedTitle)) {
                continue;
            }

            if (!isBlank(authorName) && candidate.getAuthor() != null) {

                String wantedAuthor =
                        normalizeTitle(authorName);

                String candidateAuthor =
                        normalizeTitle(candidate.getAuthor().getName());

                if (!candidateAuthor.contains(wantedAuthor)
                        && !wantedAuthor.contains(candidateAuthor)) {
                    continue;
                }
            }

            return candidate;
        }

        return null;
    }

    private static String normalizeTitle(
            String value
    ) {

        if (value == null) {
            return "";
        }

        return value.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }


    private String cleanNullable(
            String value
    ) {

        if (
                value == null
                        ||
                value.isBlank()
        ) {

            return null;
        }


        return value.trim();
    }
}