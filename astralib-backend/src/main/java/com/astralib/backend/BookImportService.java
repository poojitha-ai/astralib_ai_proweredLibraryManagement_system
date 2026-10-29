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


    // =========================================================
    // IMPORT EPUB BOOK
    // =========================================================

    public Map<String, Object> importEpub(
            MultipartFile epubFile,
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

        if (epubFile == null || epubFile.isEmpty()) {
            throw new IllegalArgumentException(
                    "EPUB file is required."
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
                epubFile.getOriginalFilename();

        if (
                originalFileName == null
                        ||
                !originalFileName
                        .toLowerCase(Locale.ROOT)
                        .endsWith(".epub")
        ) {

            throw new IllegalArgumentException(
                    "Only EPUB files are supported."
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

                book =
                        new Book();

                book.setResourceId(
                        resourceId
                );

                bookCreated = true;
            }
        }


        // =====================================================
        // AUTHOR
        // =====================================================

        Author author =
                findOrCreateAuthor(
                        authorName
                );


        // =====================================================
        // CATEGORY
        // =====================================================

        Category category =
                findOrCreateCategory(
                        categoryName
                );


        // =====================================================
        // UPDATE BOOK METADATA
        // =====================================================

        book.setTitle(
                title
        );

        book.setDescription(
                cleanNullable(description)
        );

        book.setIsbn(
                isbn
        );

        book.setPublicationYear(
                publicationYear
        );

        book.setPublisher(
                cleanNullable(publisher)
        );

        book.setLanguage(
                language == null || language.isBlank()
                        ? "English"
                        : language.trim()
        );

        book.setCoverImage(
                cleanNullable(coverImage)
        );

        book.setResourceType(
                "EPUB"
        );

        book.setAuthor(
                author
        );

        book.setCategory(
                category
        );

        book.setSubjects(
                parseSubjects(
                        subjectsText
                )
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
                slug + ".epub";


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
                    "Invalid EPUB destination path."
            );
        }


        Files.copy(
                epubFile.getInputStream(),
                destination,
                StandardCopyOption.REPLACE_EXISTING
        );


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


        DigitalResource epubResource =
                null;


        for (
                DigitalResource resource :
                existingResources
        ) {

            if (resource == null) {
                continue;
            }


            boolean epubByFileName =
                    resource.getFileName() != null
                            &&
                    resource
                            .getFileName()
                            .toLowerCase(Locale.ROOT)
                            .endsWith(".epub");


            boolean epubByFormat =
                    resource.getFormat() != null
                            &&
                    resource
                            .getFormat()
                            .equalsIgnoreCase(
                                    "application/epub+zip"
                            );


            if (
                    epubByFileName
                            ||
                    epubByFormat
            ) {

                epubResource =
                        resource;

                break;
            }
        }


        boolean resourceCreated =
                false;


        if (epubResource == null) {

            epubResource =
                    new DigitalResource();

            resourceCreated =
                    true;
        }


        epubResource.setBook(
                book
        );

        epubResource.setResourceType(
                "BOOK"
        );

        epubResource.setFileName(
                storedFileName
        );

        epubResource.setFilePath(
                portablePath
        );

        epubResource.setFormat(
                "application/epub+zip"
        );


        epubResource =
                digitalResourceRepository
                        .save(
                                epubResource
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
                epubResource.getId()
        );

        result.put(
                "fileName",
                epubResource.getFileName()
        );

        result.put(
                "filePath",
                epubResource.getFilePath()
        );

        result.put(
                "absoluteFilePath",
                destination.toAbsolutePath()
                        .normalize()
                        .toString()
        );

        result.put(
                "nextStep",
                "Run vector ingestion for book ID "
                        + book.getId()
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