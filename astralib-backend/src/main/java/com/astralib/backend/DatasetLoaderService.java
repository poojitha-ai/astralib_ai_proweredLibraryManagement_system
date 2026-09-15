package com.astralib.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class DatasetLoaderService {

    private static final Logger logger =
            LoggerFactory.getLogger(
                    DatasetLoaderService.class
            );

    // =========================================================
    // PORTABLE DATA ROOT
    // =========================================================

    /*
     * application.properties:
     *
     * astralib.data-root=${ASTRALIB_DATA_ROOT:../data}
     *
     * Default structure:
     *
     * AstraLib/
     * ├── astralib-backend/
     * └── data/
     *
     * No machine-specific absolute path is required.
     */

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
    private UserRepository userRepository;

    @Autowired
    private ScholarlyPaperRepository scholarlyPaperRepository;

    @Autowired
    private FavoriteRepository favoriteRepository;

    @Autowired
    private ReadingHistoryRepository readingHistoryRepository;

    @Autowired
    private UserInteractionRepository userInteractionRepository;

    // =========================================================
    // SERVICES
    // =========================================================

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TextPreprocessingService textPreprocessingService;

    // =========================================================
    // UTILITIES / CACHES
    // =========================================================

    private final ObjectMapper objectMapper =
            new ObjectMapper();

    private final Map<String, Long> userIdMap =
            new HashMap<>();

    private final Map<String, Long> resourceIdMap =
            new HashMap<>();

    // =========================================================
    // LOAD ALL DATASETS
    // =========================================================

    public void loadAllDatasets() {

        logger.info(
                "Starting dataset loading..."
        );

        logger.info(
                "AstraLib data root: {}",
                getDataRootPath()
        );

        try {

            loadBooks();

            loadScholarlyPapers();

            loadUsers();

            /*
             * Rebuild these mappings from the database.
             *
             * This is important when the database was restored
             * from astralib.sql rather than created from scratch.
             */
            populateUserMapCache();
            populateResourceIdMapCache();

            loadFavorites();

            /*
             * These two methods now contain seed guards.
             *
             * Synthetic history and interactions are imported
             * only when their tables are empty.
             */
            loadReadingHistory();
            loadInteractions();

            logger.info(
                    "Dataset loading completed successfully"
            );

        } catch (Exception e) {

            logger.error(
                    "Error loading datasets: {}",
                    e.getMessage(),
                    e
            );

            throw new RuntimeException(
                    "Dataset loading failed",
                    e
            );
        }
    }

    // =========================================================
    // PORTABLE FILE RESOLUTION
    // =========================================================

    /**
     * Return the configured AstraLib data directory.
     *
     * With the default configuration and backend started from:
     *
     * AstraLib/astralib-backend
     *
     * ../data resolves to:
     *
     * AstraLib/data
     */
    private Path getDataRootPath() {

        return Paths
                .get(dataRoot)
                .toAbsolutePath()
                .normalize();
    }

    /**
     * Resolve a path relative to AstraLib/data.
     *
     * Supports both:
     *
     * processed/books/file.json
     *
     * and:
     *
     * processed\books\file.json
     */
    private File resolveFile(
            String relativePath
    ) {

        if (
                relativePath == null ||
                relativePath.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "Dataset relative path cannot be empty."
            );
        }

        String portableRelativePath =
                relativePath
                        .replace(
                                "\\",
                                File.separator
                        )
                        .replace(
                                "/",
                                File.separator
                        );

        Path resolvedPath =
                getDataRootPath()
                        .resolve(
                                portableRelativePath
                        )
                        .normalize();

        /*
         * Prevent a relative path from escaping AstraLib/data.
         */
        if (
                !resolvedPath.startsWith(
                        getDataRootPath()
                )
        ) {

            throw new IllegalArgumentException(
                    "Dataset path escapes configured data root: "
                            + relativePath
            );
        }

        return resolvedPath.toFile();
    }

    // =========================================================
    // USER MAP CACHE
    // =========================================================

    private void populateUserMapCache() {

        userIdMap.clear();

        File file =
                resolveFile(
                        "synthetic/users/users.json"
                );

        if (!file.exists()) {

            logger.warn(
                    "Users dataset not found while creating user cache: {}",
                    file.getAbsolutePath()
            );

            return;
        }

        try {

            JsonNode root =
                    objectMapper.readTree(
                            file
                    );

            for (JsonNode userNode : root) {

                String userId =
                        userNode.has(
                                "user_id"
                        )
                                ? userNode
                                .get(
                                        "user_id"
                                )
                                .asText()
                                : null;

                String email =
                        userNode.has(
                                "email"
                        )
                                ? userNode
                                .get(
                                        "email"
                                )
                                .asText()
                                : null;

                if (
                        userId == null ||
                        email == null
                ) {

                    continue;
                }

                Optional<User> userOptional =
                        userRepository
                                .findByEmail(
                                        email
                                );

                userOptional.ifPresent(
                        user ->
                                userIdMap.put(
                                        userId,
                                        user.getId()
                                )
                );
            }

            logger.info(
                    "User map cache entries: {}",
                    userIdMap.size()
            );

        } catch (Exception e) {

            logger.warn(
                    "Failed to populate user map cache: {}",
                    e.getMessage()
            );
        }
    }

    // =========================================================
    // RESOURCE MAP CACHE
    // =========================================================

    private void populateResourceIdMapCache() {

        resourceIdMap.clear();

        try {

            bookRepository
                    .findAll()
                    .forEach(
                            book -> {

                                if (
                                        book.getResourceId()
                                                != null &&
                                        !book.getResourceId()
                                                .isBlank()
                                ) {

                                    resourceIdMap.put(
                                            book.getResourceId(),
                                            book.getId()
                                    );
                                }
                            }
                    );

            scholarlyPaperRepository
                    .findAll()
                    .forEach(
                            paper -> {

                                if (
                                        paper.getResourceId()
                                                != null &&
                                        !paper.getResourceId()
                                                .isBlank()
                                ) {

                                    resourceIdMap.put(
                                            paper.getResourceId(),
                                            paper.getId()
                                    );
                                }
                            }
                    );

            logger.info(
                    "Resource map cache entries: {}",
                    resourceIdMap.size()
            );

        } catch (Exception e) {

            logger.warn(
                    "Failed to populate resource ID map cache: {}",
                    e.getMessage()
            );
        }
    }

    // =========================================================
    // BOOKS
    // =========================================================

    @Transactional
    public void loadBooks() {

        File file =
                resolveFile(
                        "processed/books/books_cleaned.json"
                );

        logger.info(
                "Loading books from: {}",
                file.getAbsolutePath()
        );

        try {

            if (!file.exists()) {

                logger.warn(
                        "Books file not found: {}",
                        file.getAbsolutePath()
                );

                return;
            }

            JsonNode root =
                    objectMapper.readTree(
                            file
                    );

            int booksImported = 0;
            int authorsCreated = 0;
            int categoriesCreated = 0;

            Map<String, Author> authorCache =
                    new HashMap<>();

            authorRepository
                    .findAll()
                    .forEach(
                            author -> {

                                if (
                                        author.getName() != null
                                ) {

                                    authorCache.put(
                                            author
                                                    .getName()
                                                    .toLowerCase(),
                                            author
                                    );
                                }
                            }
                    );

            Map<String, Category> categoryCache =
                    new HashMap<>();

            categoryRepository
                    .findAll()
                    .forEach(
                            category -> {

                                if (
                                        category.getName()
                                                != null
                                ) {

                                    categoryCache.put(
                                            category
                                                    .getName()
                                                    .toLowerCase(),
                                            category
                                    );
                                }
                            }
                    );

            for (JsonNode bookNode : root) {

                try {

                    String resourceId =
                            bookNode.has(
                                    "resource_id"
                            )
                                    ? bookNode
                                    .get(
                                            "resource_id"
                                    )
                                    .asText()
                                    : null;

                    String isbn =
                            bookNode.has(
                                    "isbn"
                            )
                                    ? bookNode
                                    .get(
                                            "isbn"
                                    )
                                    .asText()
                                    : null;

                    // =========================================
                    // DUPLICATE CHECK BY RESOURCE ID
                    // =========================================

                    if (
                            resourceId != null &&
                            !resourceId.isBlank()
                    ) {

                        Optional<Book> existing =
                                bookRepository
                                        .findByResourceId(
                                                resourceId
                                        );

                        if (existing.isPresent()) {

                            resourceIdMap.put(
                                    resourceId,
                                    existing
                                            .get()
                                            .getId()
                            );

                            continue;
                        }
                    }

                    // =========================================
                    // DUPLICATE CHECK BY ISBN
                    // =========================================

                    if (
                            isbn != null &&
                            !isbn.isBlank() &&
                            bookRepository
                                    .findByIsbn(
                                            isbn
                                    )
                                    .isPresent()
                    ) {

                        continue;
                    }

                    Book book =
                            new Book();

                    book.setResourceId(
                            resourceId
                    );

                    book.setOpenLibraryKey(
                            bookNode.has(
                                    "open_library_key"
                            )
                                    ? bookNode
                                    .get(
                                            "open_library_key"
                                    )
                                    .asText()
                                    : null
                    );

                    book.setTitle(
                            bookNode.has(
                                    "title"
                            )
                                    ? bookNode
                                    .get(
                                            "title"
                                    )
                                    .asText()
                                    : "Unknown Title"
                    );

                    book.setDescription(
                            bookNode.has(
                                    "description"
                            )
                                    ? bookNode
                                    .get(
                                            "description"
                                    )
                                    .asText()
                                    : null
                    );

                    book.setIsbn(
                            isbn
                    );

                    book.setPublicationYear(
                            bookNode.has(
                                    "first_publish_year"
                            ) &&
                                    !bookNode
                                            .get(
                                                    "first_publish_year"
                                            )
                                            .isNull()

                                    ? bookNode
                                    .get(
                                            "first_publish_year"
                                    )
                                    .asInt()

                                    : null
                    );

                    book.setPublisher(
                            bookNode.has(
                                    "publisher"
                            )
                                    ? bookNode
                                    .get(
                                            "publisher"
                                    )
                                    .asText()
                                    : null
                    );

                    book.setLanguage(
                            bookNode.has(
                                    "language"
                            )
                                    ? bookNode
                                    .get(
                                            "language"
                                    )
                                    .asText()
                                    : null
                    );

                    book.setCoverImage(
                            bookNode.has(
                                    "cover_url"
                            )
                                    ? bookNode
                                    .get(
                                            "cover_url"
                                    )
                                    .asText()
                                    : null
                    );

                    book.setResourceType(
                            bookNode.has(
                                    "resource_type"
                            )
                                    ? bookNode
                                    .get(
                                            "resource_type"
                                    )
                                    .asText()
                                    : "book"
                    );

                    // =========================================
                    // SUBJECTS
                    // =========================================

                    if (
                            bookNode.has(
                                    "subjects"
                            ) &&
                                    bookNode
                                            .get(
                                                    "subjects"
                                            )
                                            .isArray()
                    ) {

                        List<String> subjects =
                                new ArrayList<>();

                        for (
                                JsonNode subject :
                                bookNode.get(
                                        "subjects"
                                )
                        ) {

                            subjects.add(
                                    subject.asText()
                            );
                        }

                        book.setSubjects(
                                subjects
                        );
                    }

                    // =========================================
                    // AUTHOR
                    // =========================================

                    if (
                            bookNode.has(
                                    "authors"
                            ) &&
                                    bookNode
                                            .get(
                                                    "authors"
                                            )
                                            .isArray() &&
                                    !bookNode
                                            .get(
                                                    "authors"
                                            )
                                            .isEmpty()
                    ) {

                        String authorName =
                                bookNode
                                        .get(
                                                "authors"
                                        )
                                        .get(0)
                                        .asText();

                        String lowerName =
                                authorName
                                        .toLowerCase();

                        Author author =
                                authorCache.get(
                                        lowerName
                                );

                        if (author == null) {

                            author =
                                    new Author(
                                            authorName,
                                            null
                                    );

                            author =
                                    authorRepository
                                            .save(
                                                    author
                                            );

                            authorCache.put(
                                    lowerName,
                                    author
                            );

                            authorsCreated++;
                        }

                        book.setAuthor(
                                author
                        );
                    }

                    // =========================================
                    // CATEGORY
                    // =========================================

                    if (
                            bookNode.has(
                                    "subjects"
                            ) &&
                                    bookNode
                                            .get(
                                                    "subjects"
                                            )
                                            .isArray() &&
                                    !bookNode
                                            .get(
                                                    "subjects"
                                            )
                                            .isEmpty()
                    ) {

                        String categoryName =
                                bookNode
                                        .get(
                                                "subjects"
                                        )
                                        .get(0)
                                        .asText();

                        String lowerCategoryName =
                                categoryName
                                        .toLowerCase();

                        Category category =
                                categoryCache.get(
                                        lowerCategoryName
                                );

                        if (category == null) {

                            category =
                                    new Category(
                                            categoryName,
                                            "Category from dataset"
                                    );

                            category =
                                    categoryRepository
                                            .save(
                                                    category
                                            );

                            categoryCache.put(
                                    lowerCategoryName,
                                    category
                            );

                            categoriesCreated++;
                        }

                        book.setCategory(
                                category
                        );
                    }

                    // =========================================
                    // TEXT CONTENT
                    // =========================================

                    book.setTextContent(
                            textPreprocessingService
                                    .generateBookTextContent(
                                            book
                                    )
                    );

                    // =========================================
                    // SAVE
                    // =========================================

                    Book savedBook =
                            bookRepository.save(
                                    book
                            );

                    booksImported++;

                    if (
                            resourceId != null &&
                            !resourceId.isBlank()
                    ) {

                        resourceIdMap.put(
                                resourceId,
                                savedBook.getId()
                        );
                    }

                } catch (Exception e) {

                    logger.warn(
                            "Failed to import book: {}",
                            e.getMessage()
                    );
                }
            }

            logger.info(
                    "Books imported: {}",
                    booksImported
            );

            logger.info(
                    "Authors created: {}",
                    authorsCreated
            );

            logger.info(
                    "Categories created: {}",
                    categoriesCreated
            );

        } catch (Exception e) {

            logger.error(
                    "Error loading books: {}",
                    e.getMessage(),
                    e
            );
        }
    }

    // =========================================================
    // SCHOLARLY PAPERS
    // =========================================================

    @Transactional
    public void loadScholarlyPapers() {

        File file =
                resolveFile(
                        "processed/scholarly/scholarly_cleaned.json"
                );

        logger.info(
                "Loading scholarly papers from: {}",
                file.getAbsolutePath()
        );

        try {

            if (!file.exists()) {

                logger.warn(
                        "Scholarly papers file not found: {}",
                        file.getAbsolutePath()
                );

                return;
            }

            JsonNode root =
                    objectMapper.readTree(
                            file
                    );

            int papersImported = 0;

            for (JsonNode paperNode : root) {

                try {

                    String resourceId =
                            paperNode.has(
                                    "resource_id"
                            )
                                    ? paperNode
                                    .get(
                                            "resource_id"
                                    )
                                    .asText()
                                    : null;

                    // =========================================
                    // DUPLICATE CHECK
                    // =========================================

                    if (
                            resourceId != null &&
                            !resourceId.isBlank()
                    ) {

                        Optional<ScholarlyPaper> existing =
                                scholarlyPaperRepository
                                        .findByResourceId(
                                                resourceId
                                        );

                        if (existing.isPresent()) {

                            resourceIdMap.put(
                                    resourceId,
                                    existing
                                            .get()
                                            .getId()
                            );

                            continue;
                        }
                    }

                    ScholarlyPaper paper =
                            new ScholarlyPaper();

                    paper.setResourceId(
                            resourceId
                    );

                    paper.setOpenalexId(
                            paperNode.has(
                                    "openalex_id"
                            )
                                    ? paperNode
                                    .get(
                                            "openalex_id"
                                    )
                                    .asText()
                                    : null
                    );

                    paper.setDoi(
                            paperNode.has(
                                    "doi"
                            )
                                    ? paperNode
                                    .get(
                                            "doi"
                                    )
                                    .asText()
                                    : null
                    );

                    paper.setTitle(
                            paperNode.has(
                                    "title"
                            )
                                    ? paperNode
                                    .get(
                                            "title"
                                    )
                                    .asText()
                                    : "Unknown Title"
                    );

                    paper.setPublicationYear(
                            paperNode.has(
                                    "publication_year"
                            ) &&
                                    !paperNode
                                            .get(
                                                    "publication_year"
                                            )
                                            .isNull()

                                    ? paperNode
                                    .get(
                                            "publication_year"
                                    )
                                    .asInt()

                                    : null
                    );

                    paper.setAbstractText(
                            paperNode.has(
                                    "abstract"
                            )
                                    ? paperNode
                                    .get(
                                            "abstract"
                                    )
                                    .asText()
                                    : null
                    );

                    paper.setVenue(
                            paperNode.has(
                                    "venue"
                            )
                                    ? paperNode
                                    .get(
                                            "venue"
                                    )
                                    .asText()
                                    : null
                    );

                    paper.setPdfUrl(
                            paperNode.has(
                                    "pdf_url"
                            )
                                    ? paperNode
                                    .get(
                                            "pdf_url"
                                    )
                                    .asText()
                                    : null
                    );

                    paper.setIsOpenAccess(
                            paperNode.has(
                                    "is_open_access"
                            ) &&
                                    paperNode
                                            .get(
                                                    "is_open_access"
                                            )
                                            .asBoolean()
                    );

                    paper.setCitedByCount(
                            paperNode.has(
                                    "cited_by_count"
                            ) &&
                                    !paperNode
                                            .get(
                                                    "cited_by_count"
                                            )
                                            .isNull()

                                    ? paperNode
                                    .get(
                                            "cited_by_count"
                                    )
                                    .asInt()

                                    : null
                    );

                    paper.setResourceType(
                            paperNode.has(
                                    "resource_type"
                            )
                                    ? paperNode
                                    .get(
                                            "resource_type"
                                    )
                                    .asText()
                                    : "paper"
                    );

                    // =========================================
                    // CONCEPTS
                    // =========================================

                    if (
                            paperNode.has(
                                    "concepts"
                            ) &&
                                    paperNode
                                            .get(
                                                    "concepts"
                                            )
                                            .isArray()
                    ) {

                        List<String> concepts =
                                new ArrayList<>();

                        for (
                                JsonNode concept :
                                paperNode.get(
                                        "concepts"
                                )
                        ) {

                            if (
                                    concept.has(
                                            "name"
                                    )
                            ) {

                                concepts.add(
                                        concept
                                                .get(
                                                        "name"
                                                )
                                                .asText()
                                );

                            } else if (
                                    concept.isTextual()
                            ) {

                                concepts.add(
                                        concept.asText()
                                );
                            }
                        }

                        paper.setConcepts(
                                concepts
                        );
                    }

                    // =========================================
                    // AUTHORS
                    // =========================================

                    if (
                            paperNode.has(
                                    "authors"
                            ) &&
                                    paperNode
                                            .get(
                                                    "authors"
                                            )
                                            .isArray()
                    ) {

                        List<String> authors =
                                new ArrayList<>();

                        for (
                                JsonNode author :
                                paperNode.get(
                                        "authors"
                                )
                        ) {

                            if (
                                    author.has(
                                            "name"
                                    )
                            ) {

                                authors.add(
                                        author
                                                .get(
                                                        "name"
                                                )
                                                .asText()
                                );

                            } else if (
                                    author.isTextual()
                            ) {

                                authors.add(
                                        author.asText()
                                );
                            }
                        }

                        paper.setAuthors(
                                authors
                        );
                    }

                    // =========================================
                    // REFERENCED WORKS
                    // =========================================

                    if (
                            paperNode.has(
                                    "referenced_works"
                            ) &&
                                    paperNode
                                            .get(
                                                    "referenced_works"
                                            )
                                            .isArray()
                    ) {

                        List<String> references =
                                new ArrayList<>();

                        for (
                                JsonNode reference :
                                paperNode.get(
                                        "referenced_works"
                                )
                        ) {

                            references.add(
                                    reference.asText()
                            );
                        }

                        paper.setReferencedWorks(
                                references
                        );
                    }

                    paper.setTextContent(
                            textPreprocessingService
                                    .generatePaperTextContent(
                                            paper
                                    )
                    );

                    ScholarlyPaper savedPaper =
                            scholarlyPaperRepository
                                    .save(
                                            paper
                                    );

                    papersImported++;

                    if (
                            resourceId != null &&
                            !resourceId.isBlank()
                    ) {

                        resourceIdMap.put(
                                resourceId,
                                savedPaper.getId()
                        );
                    }

                } catch (Exception e) {

                    logger.warn(
                            "Failed to import scholarly paper: {}",
                            e.getMessage()
                    );
                }
            }

            logger.info(
                    "Scholarly papers imported: {}",
                    papersImported
            );

        } catch (Exception e) {

            logger.error(
                    "Error loading scholarly papers: {}",
                    e.getMessage(),
                    e
            );
        }
    }

    // =========================================================
    // USERS
    // =========================================================

    @Transactional
    public void loadUsers() {

        File file =
                resolveFile(
                        "synthetic/users/users.json"
                );

        logger.info(
                "Loading users from: {}",
                file.getAbsolutePath()
        );

        try {

            if (!file.exists()) {

                logger.warn(
                        "Users file not found: {}",
                        file.getAbsolutePath()
                );

                return;
            }

            JsonNode root =
                    objectMapper.readTree(
                            file
                    );

            int usersImported = 0;

            for (JsonNode userNode : root) {

                try {

                    String userId =
                            userNode.has(
                                    "user_id"
                            )
                                    ? userNode
                                    .get(
                                            "user_id"
                                    )
                                    .asText()
                                    : null;

                    String email =
                            userNode.has(
                                    "email"
                            )
                                    ? userNode
                                    .get(
                                            "email"
                                    )
                                    .asText()
                                    : null;

                    // =========================================
                    // EXISTING USER
                    // =========================================

                    if (
                            email != null &&
                            userRepository
                                    .existsByEmail(
                                            email
                                    )
                    ) {

                        Optional<User> existing =
                                userRepository
                                        .findByEmail(
                                                email
                                        );

                        if (
                                existing.isPresent() &&
                                userId != null
                        ) {

                            userIdMap.put(
                                    userId,
                                    existing
                                            .get()
                                            .getId()
                            );
                        }

                        continue;
                    }

                    User user =
                            new User();

                    user.setName(
                            userNode.has(
                                    "full_name"
                            )
                                    ? userNode
                                    .get(
                                            "full_name"
                                    )
                                    .asText()
                                    : "Unknown User"
                    );

                    user.setEmail(
                            email != null
                                    ? email
                                    : "user_"
                                    + UUID.randomUUID()
                                    + "@astralib.edu"
                    );

                    user.setPassword(
                            passwordEncoder.encode(
                                    "defaultPassword123"
                            )
                    );

                    user.setRole(
                            Role.USER
                    );

                    user.setCreatedAt(
                            LocalDateTime.now()
                    );

                    User savedUser =
                            userRepository.save(
                                    user
                            );

                    usersImported++;

                    if (userId != null) {

                        userIdMap.put(
                                userId,
                                savedUser.getId()
                        );
                    }

                } catch (Exception e) {

                    logger.warn(
                            "Failed to import user: {}",
                            e.getMessage()
                    );
                }
            }

            logger.info(
                    "Users imported: {}",
                    usersImported
            );

        } catch (Exception e) {

            logger.error(
                    "Error loading users: {}",
                    e.getMessage(),
                    e
            );
        }
    }

    // =========================================================
    // FAVORITES
    // =========================================================

    @Transactional
    public void loadFavorites() {

        File file =
                resolveFile(
                        "synthetic/favorites/favorites.json"
                );

        logger.info(
                "Loading favorites from: {}",
                file.getAbsolutePath()
        );

        try {

            if (!file.exists()) {

                logger.warn(
                        "Favorites file not found: {}",
                        file.getAbsolutePath()
                );

                return;
            }

            JsonNode root =
                    objectMapper.readTree(
                            file
                    );

            int favoritesImported = 0;

            for (JsonNode favoriteNode : root) {

                try {

                    String userId =
                            favoriteNode.has(
                                    "user_id"
                            )
                                    ? favoriteNode
                                    .get(
                                            "user_id"
                                    )
                                    .asText()
                                    : null;

                    String resourceId =
                            favoriteNode.has(
                                    "resource_id"
                            )
                                    ? favoriteNode
                                    .get(
                                            "resource_id"
                                    )
                                    .asText()
                                    : null;

                    String resourceType =
                            favoriteNode.has(
                                    "resource_type"
                            )
                                    ? favoriteNode
                                    .get(
                                            "resource_type"
                                    )
                                    .asText()
                                    : null;

                    if (
                            userId == null ||
                            !userIdMap.containsKey(
                                    userId
                            )
                    ) {

                        continue;
                    }

                    Long databaseUserId =
                            userIdMap.get(
                                    userId
                            );

                    Optional<User> userOptional =
                            userRepository
                                    .findById(
                                            databaseUserId
                                    );

                    if (userOptional.isEmpty()) {

                        continue;
                    }

                    if (
                            !"book".equalsIgnoreCase(
                                    resourceType
                            )
                    ) {

                        continue;
                    }

                    Long bookId =
                            resourceIdMap.get(
                                    resourceId
                            );

                    if (bookId == null) {

                        Optional<Book> bookOptional =
                                bookRepository
                                        .findByResourceId(
                                                resourceId
                                        );

                        if (bookOptional.isPresent()) {

                            bookId =
                                    bookOptional
                                            .get()
                                            .getId();

                        } else {

                            continue;
                        }
                    }

                    Optional<Book> bookOptional =
                            bookRepository
                                    .findById(
                                            bookId
                                    );

                    if (bookOptional.isEmpty()) {

                        continue;
                    }

                    /*
                     * Favorites already have their own
                     * duplicate protection.
                     */
                    if (
                            favoriteRepository
                                    .findByUser_IdAndBook_Id(
                                            databaseUserId,
                                            bookId
                                    )
                                    .isPresent()
                    ) {

                        continue;
                    }

                    Favorite favorite =
                            new Favorite(
                                    userOptional.get(),
                                    bookOptional.get()
                            );

                    favoriteRepository.save(
                            favorite
                    );

                    favoritesImported++;

                } catch (Exception e) {

                    logger.warn(
                            "Failed to import favorite: {}",
                            e.getMessage()
                    );
                }
            }

            logger.info(
                    "Favorites imported: {}",
                    favoritesImported
            );

        } catch (Exception e) {

            logger.error(
                    "Error loading favorites: {}",
                    e.getMessage(),
                    e
            );
        }
    }

    // =========================================================
    // READING HISTORY
    // =========================================================

    @Transactional
    public void loadReadingHistory() {

        /*
         * =====================================================
         * IMPORTANT SEED PROTECTION
         * =====================================================
         *
         * reading_history represents historical events and
         * real user progress.
         *
         * Previously AstraLib re-imported the synthetic
         * reading-history dataset every time Spring Boot
         * started, causing hundreds of thousands of duplicates.
         *
         * The seed dataset is now imported ONLY when the table
         * is completely empty.
         *
         * After a database restore, this table already contains
         * data, therefore startup safely skips this import.
         */

        long existingCount =
                readingHistoryRepository.count();

        if (existingCount > 0) {

            logger.info(
                    "Reading history already contains {} rows. "
                            + "Skipping synthetic seed import.",
                    existingCount
            );

            return;
        }

        File file =
                resolveFile(
                        "synthetic/reading_history/reading_history.json"
                );

        logger.info(
                "Loading reading history from: {}",
                file.getAbsolutePath()
        );

        try {

            if (!file.exists()) {

                logger.warn(
                        "Reading history file not found: {}",
                        file.getAbsolutePath()
                );

                return;
            }

            JsonNode root =
                    objectMapper.readTree(
                            file
                    );

            int historyImported = 0;

            DateTimeFormatter formatter =
                    DateTimeFormatter.ISO_DATE_TIME;

            for (JsonNode historyNode : root) {

                try {

                    String userId =
                            historyNode.has(
                                    "user_id"
                            )
                                    ? historyNode
                                    .get(
                                            "user_id"
                                    )
                                    .asText()
                                    : null;

                    String resourceId =
                            historyNode.has(
                                    "resource_id"
                            )
                                    ? historyNode
                                    .get(
                                            "resource_id"
                                    )
                                    .asText()
                                    : null;

                    String resourceType =
                            historyNode.has(
                                    "resource_type"
                            )
                                    ? historyNode
                                    .get(
                                            "resource_type"
                                    )
                                    .asText()
                                    : null;

                    if (
                            userId == null ||
                            !userIdMap.containsKey(
                                    userId
                            )
                    ) {

                        continue;
                    }

                    Long databaseUserId =
                            userIdMap.get(
                                    userId
                            );

                    Optional<User> userOptional =
                            userRepository
                                    .findById(
                                            databaseUserId
                                    );

                    if (userOptional.isEmpty()) {

                        continue;
                    }

                    if (
                            !"book".equalsIgnoreCase(
                                    resourceType
                            )
                    ) {

                        continue;
                    }

                    Long bookId =
                            resourceIdMap.get(
                                    resourceId
                            );

                    if (bookId == null) {

                        Optional<Book> bookOptional =
                                bookRepository
                                        .findByResourceId(
                                                resourceId
                                        );

                        if (bookOptional.isPresent()) {

                            bookId =
                                    bookOptional
                                            .get()
                                            .getId();

                        } else {

                            continue;
                        }
                    }

                    Optional<Book> bookOptional =
                            bookRepository
                                    .findById(
                                            bookId
                                    );

                    if (bookOptional.isEmpty()) {

                        continue;
                    }

                    ReadingHistory history =
                            new ReadingHistory(
                                    userOptional.get(),
                                    bookOptional.get(),
                                    null
                            );

                    if (
                            historyNode.has(
                                    "timestamp"
                            )
                    ) {

                        try {

                            LocalDateTime timestamp =
                                    LocalDateTime.parse(
                                            historyNode
                                                    .get(
                                                            "timestamp"
                                                    )
                                                    .asText(),
                                            formatter
                                    );

                            history.setAccessTime(
                                    timestamp
                            );

                        } catch (Exception e) {

                            history.setAccessTime(
                                    LocalDateTime.now()
                            );
                        }
                    }

                    readingHistoryRepository.save(
                            history
                    );

                    historyImported++;

                } catch (Exception e) {

                    logger.warn(
                            "Failed to import reading history: {}",
                            e.getMessage()
                    );
                }
            }

            logger.info(
                    "Reading history imported: {}",
                    historyImported
            );

        } catch (Exception e) {

            logger.error(
                    "Error loading reading history: {}",
                    e.getMessage(),
                    e
            );
        }
    }

    // =========================================================
    // USER INTERACTIONS
    // =========================================================

    @Transactional
    public void loadInteractions() {

        /*
         * =====================================================
         * IMPORTANT SEED PROTECTION
         * =====================================================
         *
         * The synthetic interaction dataset must not be
         * inserted again on every Spring Boot restart.
         *
         * Import it only when user_interactions is empty.
         */

        long existingCount =
                userInteractionRepository.count();

        if (existingCount > 0) {

            logger.info(
                    "User interactions already contain {} rows. "
                            + "Skipping synthetic seed import.",
                    existingCount
            );

            return;
        }

        File file =
                resolveFile(
                        "synthetic/search_history/interactions.json"
                );

        logger.info(
                "Loading interactions from: {}",
                file.getAbsolutePath()
        );

        try {

            if (!file.exists()) {

                logger.warn(
                        "Interactions file not found: {}",
                        file.getAbsolutePath()
                );

                return;
            }

            JsonNode root =
                    objectMapper.readTree(
                            file
                    );

            int interactionsImported = 0;

            DateTimeFormatter formatter =
                    DateTimeFormatter.ISO_DATE_TIME;

            for (JsonNode interactionNode : root) {

                try {

                    String userId =
                            interactionNode.has(
                                    "user_id"
                            )
                                    ? interactionNode
                                    .get(
                                            "user_id"
                                    )
                                    .asText()
                                    : null;

                    String resourceId =
                            interactionNode.has(
                                    "resource_id"
                            )
                                    ? interactionNode
                                    .get(
                                            "resource_id"
                                    )
                                    .asText()
                                    : null;

                    String resourceType =
                            interactionNode.has(
                                    "resource_type"
                            )
                                    ? interactionNode
                                    .get(
                                            "resource_type"
                                    )
                                    .asText()
                                    : null;

                    String action =
                            interactionNode.has(
                                    "action"
                            )
                                    ? interactionNode
                                    .get(
                                            "action"
                                    )
                                    .asText()
                                    : null;

                    if (
                            userId == null ||
                            !userIdMap.containsKey(
                                    userId
                            )
                    ) {

                        continue;
                    }

                    Long databaseUserId =
                            userIdMap.get(
                                    userId
                            );

                    Optional<User> userOptional =
                            userRepository
                                    .findById(
                                            databaseUserId
                                    );

                    if (userOptional.isEmpty()) {

                        continue;
                    }

                    UserInteraction interaction =
                            new UserInteraction();

                    interaction.setUser(
                            userOptional.get()
                    );

                    interaction.setResourceId(
                            resourceId
                    );

                    interaction.setResourceType(
                            resourceType
                    );

                    interaction.setAction(
                            action
                    );

                    if (
                            interactionNode.has(
                                    "timestamp"
                            )
                    ) {

                        try {

                            LocalDateTime timestamp =
                                    LocalDateTime.parse(
                                            interactionNode
                                                    .get(
                                                            "timestamp"
                                                    )
                                                    .asText(),
                                            formatter
                                    );

                            interaction.setTimestamp(
                                    timestamp
                            );

                        } catch (Exception e) {

                            interaction.setTimestamp(
                                    LocalDateTime.now()
                            );
                        }
                    }

                    if (
                            interactionNode.has(
                                    "duration_seconds"
                            ) &&
                                    !interactionNode
                                            .get(
                                                    "duration_seconds"
                                            )
                                            .isNull()
                    ) {

                        interaction.setDurationSeconds(
                                interactionNode
                                        .get(
                                                "duration_seconds"
                                        )
                                        .asInt()
                        );
                    }

                    if (
                            interactionNode.has(
                                    "completion_percentage"
                            ) &&
                                    !interactionNode
                                            .get(
                                                    "completion_percentage"
                                            )
                                            .isNull()
                    ) {

                        interaction.setCompletionPercentage(
                                interactionNode
                                        .get(
                                                "completion_percentage"
                                        )
                                        .asInt()
                        );
                    }

                    userInteractionRepository.save(
                            interaction
                    );

                    interactionsImported++;

                } catch (Exception e) {

                    logger.warn(
                            "Failed to import interaction: {}",
                            e.getMessage()
                    );
                }
            }

            logger.info(
                    "Activities processed: {}",
                    interactionsImported
            );

        } catch (Exception e) {

            logger.error(
                    "Error loading interactions: {}",
                    e.getMessage(),
                    e
            );
        }
    }

    // =========================================================
    // GETTERS
    // =========================================================

    public Map<String, Long> getUserIdMap() {

        return userIdMap;
    }

    public Map<String, Long> getResourceIdMap() {

        return resourceIdMap;
    }
}