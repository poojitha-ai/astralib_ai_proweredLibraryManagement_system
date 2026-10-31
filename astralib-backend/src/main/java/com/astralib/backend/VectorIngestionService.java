package com.astralib.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;


@Service
public class VectorIngestionService {

    private static final Logger logger =
            LoggerFactory.getLogger(
                    VectorIngestionService.class
            );


    // =========================================================
    // PORTABLE ASTRA LIB DATA ROOT
    // =========================================================
    //
    // application.properties:
    //
    // astralib.data-root=${ASTRALIB_DATA_ROOT:../data}
    //
    // Default project layout:
    //
    // AstraLib/
    // ├── astralib-backend/
    // ├── astralib-frontend/
    // └── data/
    //
    // =========================================================

    @Value("${astralib.data-root:../data}")
    private String dataRoot;


    /*
     * Files uploaded through ResourceController are stored under this
     * root and registered in digital_resources as "uploads/<file>".
     * The resolver below must know about it, otherwise an uploaded
     * EPUB is looked up under the data root and never found.
     */
    @Value("${astralib.upload-root:uploads}")
    private String uploadRoot;


    // =========================================================
    // REPOSITORIES
    // =========================================================

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private ScholarlyPaperRepository scholarlyPaperRepository;

    @Autowired
    private VectorRecordRepository vectorRecordRepository;

    @Autowired
    private DigitalResourceRepository digitalResourceRepository;


    // =========================================================
    // SERVICES
    // =========================================================

    @Autowired
    private TextPreprocessingService textPreprocessingService;

    @Autowired
    private DocumentChunkerService documentChunkerService;

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private EpubTextExtractionService epubTextExtractionService;

    /*
     * Books arrive as EPUB, PDF or HTML. Extraction is delegated so every
     * format feeds the same chunk -> embedding -> vector path.
     */
    @Autowired
    private DocumentExtractionService documentExtractionService;


    private final ObjectMapper objectMapper =
            new ObjectMapper();


    // =========================================================
    // EMBEDDING RATE-LIMIT PROTECTION
    // =========================================================

    private static final long EMBEDDING_MIN_INTERVAL_MS =
            700L;

    /*
     * The interval above protects the Gemini free-tier per-minute
     * quota. The local sentence-transformers service has no such
     * quota, so pacing it only makes indexing a whole book take hours.
     */
    @Value("${llm.embedding-provider:none}")
    private String embeddingProvider;

    private static final int EMBEDDING_MAX_RETRIES =
            5;

    private static final long EMBEDDING_INITIAL_RETRY_DELAY_MS =
            8_000L;

    private static final long EMBEDDING_MAX_RETRY_DELAY_MS =
            60_000L;


    private long lastEmbeddingRequestAt =
            0L;


    // =========================================================
    // RE-INGEST EVERYTHING
    // =========================================================
    //
    // IMPORTANT:
    // This deletes every existing vector record.
    //
    // Do not use this while testing only one book.
    // =========================================================

    public Map<String, Object> reingestAllVectors() {

        vectorRecordRepository
                .deleteAllInBatch();


        return ingestAllVectors();
    }


    // =========================================================
    // INGEST ALL DATA
    // =========================================================

    public Map<String, Object> ingestAllVectors() {

        logger.info(
                "Starting vector embedding ingestion..."
        );


        logger.info(
                "AstraLib data root: {}",
                getDataRootPath()
        );


        int documentsProcessed = 0;
        int chunksCreated = 0;
        int embeddingsGenerated = 0;
        int vectorsStored = 0;
        int duplicatesSkipped = 0;
        int failedRecords = 0;


        List<VectorRecord> batchBuffer =
                new ArrayList<>(200);


        // =====================================================
        // 1. PREPARED RAG DOCUMENTS
        // =====================================================

        logger.info(
                "Ingesting prepared RAG documents..."
        );


        try {

            File ragFile =
                    resolveFile(
                            "processed/documents/documents_full_rag.json"
                    );


            logger.info(
                    "RAG dataset path: {}",
                    ragFile.getAbsolutePath()
            );


            if (
                    ragFile.exists()
                            &&
                    ragFile.isFile()
            ) {

                JsonNode root =
                        objectMapper.readTree(
                                ragFile
                        );


                for (
                        JsonNode docNode :
                        root
                ) {

                    try {

                        documentsProcessed++;


                        String docId =
                                docNode.has(
                                        "document_id"
                                )
                                        ?
                                        docNode
                                                .get(
                                                        "document_id"
                                                )
                                                .asText()
                                        :
                                        "DOC-"
                                                +
                                        UUID.randomUUID();


                        String title =
                                docNode.has(
                                        "title"
                                )
                                        ?
                                        docNode
                                                .get(
                                                        "title"
                                                )
                                                .asText()
                                        :
                                        "Untitled Document";


                        // =====================================
                        // AUTHORS
                        // =====================================

                        List<String> authorsList =
                                new ArrayList<>();


                        if (
                                docNode.has(
                                        "authors"
                                )
                                        &&
                                docNode
                                        .get(
                                                "authors"
                                        )
                                        .isArray()
                        ) {

                            for (
                                    JsonNode authorNode :
                                    docNode.get(
                                            "authors"
                                    )
                            ) {

                                authorsList.add(
                                        authorNode.asText()
                                );
                            }
                        }


                        String author =
                                authorsList.isEmpty()
                                        ?
                                        "Unknown Author"
                                        :
                                        String.join(
                                                ", ",
                                                authorsList
                                        );


                        // =====================================
                        // CATEGORIES
                        // =====================================

                        List<String> categoryList =
                                new ArrayList<>();


                        if (
                                docNode.has(
                                        "categories"
                                )
                                        &&
                                docNode
                                        .get(
                                                "categories"
                                        )
                                        .isArray()
                        ) {

                            for (
                                    JsonNode categoryNode :
                                    docNode.get(
                                            "categories"
                                    )
                            ) {

                                categoryList.add(
                                        categoryNode.asText()
                                );
                            }
                        }


                        String category =
                                categoryList.isEmpty()
                                        ?
                                        "ArXiv Document"
                                        :
                                        String.join(
                                                ", ",
                                                categoryList
                                        );


                        // =====================================
                        // PREPARED CHUNKS
                        // =====================================

                        if (
                                docNode.has(
                                        "chunks"
                                )
                                        &&
                                docNode
                                        .get(
                                                "chunks"
                                        )
                                        .isArray()
                        ) {

                            int chunkIndex =
                                    0;


                            for (
                                    JsonNode chunkNode :
                                    docNode.get(
                                            "chunks"
                                    )
                            ) {

                                String chunkId =
                                        chunkNode.has(
                                                "chunk_id"
                                        )
                                                ?
                                                chunkNode
                                                        .get(
                                                                "chunk_id"
                                                        )
                                                        .asText()
                                                :
                                                docId
                                                        +
                                                "-CHK-"
                                                        +
                                                chunkIndex;


                                String section =
                                        chunkNode.has(
                                                "section"
                                        )
                                                ?
                                                chunkNode
                                                        .get(
                                                                "section"
                                                        )
                                                        .asText()
                                                :
                                                "Main";


                                String content =
                                        chunkNode.has(
                                                "content"
                                        )
                                                ?
                                                chunkNode
                                                        .get(
                                                                "content"
                                                        )
                                                        .asText()
                                                :
                                                "";


                                if (
                                        content == null
                                                ||
                                        content.isBlank()
                                ) {

                                    chunkIndex++;

                                    continue;
                                }


                                chunksCreated++;


                                if (
                                        vectorRecordRepository
                                                .existsByChunkId(
                                                        chunkId
                                                )
                                ) {

                                    duplicatesSkipped++;

                                    chunkIndex++;

                                    continue;
                                }


                                float[] embedding =
                                        generateEmbeddingWithRetry(
                                                title
                                                        +
                                                "\n"
                                                        +
                                                section
                                                        +
                                                "\n"
                                                        +
                                                content
                                        );


                                embeddingsGenerated++;


                                VectorRecord record =
                                        new VectorRecord(
                                                chunkId,
                                                docId,
                                                "document",
                                                title,
                                                author,
                                                category,
                                                section,
                                                chunkIndex,
                                                content,
                                                objectMapper
                                                        .writeValueAsString(
                                                                embedding
                                                        )
                                        );


                                batchBuffer.add(
                                        record
                                );


                                vectorsStored++;

                                chunkIndex++;


                                if (
                                        batchBuffer.size()
                                                >=
                                        200
                                ) {

                                    vectorRecordRepository
                                            .saveAll(
                                                    batchBuffer
                                            );


                                    batchBuffer.clear();
                                }
                            }
                        }


                    } catch (
                            DailyEmbeddingQuotaExceededException e
                    ) {

                        logger.warn(
                                "Daily embedding quota exhausted while ingesting prepared RAG documents."
                        );


                        flushBatch(
                                batchBuffer
                        );


                        throw e;


                    } catch (Exception e) {

                        failedRecords++;


                        logger.warn(
                                "Failed to ingest RAG document: {}",
                                e.getMessage()
                        );
                    }
                }

            } else {

                logger.warn(
                        "Prepared RAG file not found: {}",
                        ragFile.getAbsolutePath()
                );
            }


        } catch (
                DailyEmbeddingQuotaExceededException e
        ) {

            logger.warn(
                    "Stopping prepared RAG ingestion because daily embedding quota is exhausted."
            );


        } catch (Exception e) {

            logger.error(
                    "Error loading RAG documents file: {}",
                    e.getMessage()
            );
        }


        flushBatch(
                batchBuffer
        );


        // =====================================================
        // 2. BOOKS
        // =====================================================

        logger.info(
                "Ingesting book vectors..."
        );


        List<Book> books =
                bookRepository.findAll();


        for (
                Book book :
                books
        ) {

            try {

                Map<String, Integer> stats =
                        ingestBookInternal(
                                book,
                                false,
                                batchBuffer
                        );


                documentsProcessed +=
                        stats.getOrDefault(
                                "documentsProcessed",
                                0
                        );


                chunksCreated +=
                        stats.getOrDefault(
                                "chunksCreated",
                                0
                        );


                embeddingsGenerated +=
                        stats.getOrDefault(
                                "embeddingsGenerated",
                                0
                        );


                vectorsStored +=
                        stats.getOrDefault(
                                "vectorsStored",
                                0
                        );


                duplicatesSkipped +=
                        stats.getOrDefault(
                                "duplicatesSkipped",
                                0
                        );


                failedRecords +=
                        stats.getOrDefault(
                                "failedRecords",
                                0
                        );


            } catch (
                    DailyEmbeddingQuotaExceededException e
            ) {

                logger.warn(
                        "Daily embedding quota exhausted while ingesting books. Existing vectors are preserved."
                );


                flushBatch(
                        batchBuffer
                );


                break;


            } catch (Exception e) {

                failedRecords++;


                logger.warn(
                        "Failed to ingest book ID {}: {}",
                        book.getId(),
                        e.getMessage()
                );
            }
        }


        flushBatch(
                batchBuffer
        );


        // =====================================================
        // 3. SCHOLARLY PAPERS
        // =====================================================

        logger.info(
                "Ingesting scholarly paper vectors..."
        );


        List<ScholarlyPaper> papers =
                scholarlyPaperRepository.findAll();


        for (
                ScholarlyPaper paper :
                papers
        ) {

            try {

                documentsProcessed++;


                String docId =
                        paper.getResourceId() != null
                                ?
                                paper.getResourceId()
                                :
                                "PAPER-ID-"
                                        +
                                paper.getId();


                String title =
                        paper.getTitle();


                String author =
                        paper.getAuthors() != null
                                &&
                        !paper
                                .getAuthors()
                                .isEmpty()
                                ?
                                String.join(
                                        ", ",
                                        paper.getAuthors()
                                )
                                :
                                "Unknown Author";


                String category =
                        paper.getVenue() != null
                                ?
                                paper.getVenue()
                                :
                                "Scholarly Paper";


                String text =
                        paper.getTextContent();


                if (
                        text == null
                                ||
                        text.isBlank()
                ) {

                    text =
                            textPreprocessingService
                                    .generatePaperTextContent(
                                            paper
                                    );
                }


                List<DocumentChunkerService.Chunk> chunks =
                        documentChunkerService
                                .chunkText(
                                        docId,
                                        title,
                                        author,
                                        category,
                                        "paper",
                                        text
                                );


                int chunkIndex =
                        0;


                for (
                        DocumentChunkerService.Chunk chunk :
                        chunks
                ) {

                    chunksCreated++;


                    if (
                            vectorRecordRepository
                                    .existsByChunkId(
                                            chunk.getChunkId()
                                    )
                    ) {

                        duplicatesSkipped++;

                        chunkIndex++;

                        continue;
                    }


                    float[] embedding =
                            generateEmbeddingWithRetry(
                                    chunk.getContent()
                            );


                    embeddingsGenerated++;


                    VectorRecord record =
                            new VectorRecord(
                                    chunk.getChunkId(),
                                    chunk.getDocumentId(),
                                    chunk.getDocumentType(),
                                    chunk.getTitle(),
                                    chunk.getAuthor(),
                                    chunk.getCategory(),
                                    chunk.getSection(),
                                    chunkIndex,
                                    chunk.getContent(),
                                    objectMapper
                                            .writeValueAsString(
                                                    embedding
                                            )
                            );


                    batchBuffer.add(
                            record
                    );


                    vectorsStored++;

                    chunkIndex++;


                    if (
                            batchBuffer.size()
                                    >=
                            200
                    ) {

                        vectorRecordRepository
                                .saveAll(
                                        batchBuffer
                                );


                        batchBuffer.clear();
                    }
                }


            } catch (
                    DailyEmbeddingQuotaExceededException e
            ) {

                logger.warn(
                        "Daily embedding quota exhausted while ingesting scholarly papers. Existing vectors are preserved."
                );


                flushBatch(
                        batchBuffer
                );


                break;


            } catch (Exception e) {

                failedRecords++;


                logger.warn(
                        "Failed to ingest paper ID {}: {}",
                        paper.getId(),
                        e.getMessage()
                );
            }
        }


        flushBatch(
                batchBuffer
        );


        // =====================================================
        // RETURN STATISTICS
        // =====================================================

        Map<String, Object> stats =
                new LinkedHashMap<>();


        stats.put(
                "documentsProcessed",
                documentsProcessed
        );


        stats.put(
                "chunksCreated",
                chunksCreated
        );


        stats.put(
                "vectorsStored",
                vectorsStored
        );


        stats.put(
                "duplicatesSkipped",
                duplicatesSkipped
        );


        stats.put(
                "embeddingsGenerated",
                embeddingsGenerated
        );


        stats.put(
                "totalVectorRecordsInDb",
                vectorRecordRepository.count()
        );


        stats.put(
                "failedRecords",
                failedRecords
        );


        logger.info(
                "Vector ingestion completed: {}",
                stats
        );


        return stats;
    }


    // =========================================================
    // INGEST ONLY ONE BOOK
    // =========================================================
    //
    // Resume mode:
    //
    // Existing vectors are NOT deleted.
    //
    // Existing chunk IDs are skipped.
    //
    // This is the safe endpoint used for Sherlock.
    // =========================================================

    public Map<String, Object> ingestBookVectors(
            Long bookId
    ) {

        Optional<Book> bookOptional =
                bookRepository.findById(
                        bookId
                );


        if (
                bookOptional.isEmpty()
        ) {

            throw new IllegalArgumentException(
                    "Book not found: "
                            +
                    bookId
            );
        }


        Book book =
                bookOptional.get();


        String documentId =
                getBookDocumentId(
                        book
                );


        List<VectorRecord> batch =
                new ArrayList<>();


        Map<String, Integer> internalStats =
                ingestBookInternal(
                        book,
                        false,
                        batch
                );


        flushBatch(
                batch
        );


        Map<String, Object> result =
                new LinkedHashMap<>();


        result.put(
                "bookId",
                book.getId()
        );


        result.put(
                "documentId",
                documentId
        );


        result.put(
                "title",
                book.getTitle()
        );


        result.putAll(
                internalStats
        );


        result.put(
                "vectorRecordsForBook",
                vectorRecordRepository
                        .findByDocumentId(
                                documentId
                        )
                        .size()
        );


        result.put(
                "totalVectorRecordsInDb",
                vectorRecordRepository
                        .count()
        );


        return result;
    }


    // =========================================================
    // INTERNAL BOOK INGESTION
    // =========================================================

    private Map<String, Integer> ingestBookInternal(
            Book book,
            boolean force,
            List<VectorRecord> batchBuffer
    ) {

        Map<String, Integer> stats =
                new LinkedHashMap<>();


        int documentsProcessed =
                1;

        int chunksCreated =
                0;

        int embeddingsGenerated =
                0;

        int vectorsStored =
                0;

        int duplicatesSkipped =
                0;

        int failedRecords =
                0;


        try {

            String docId =
                    getBookDocumentId(
                            book
                    );


            String title =
                    book.getTitle() != null
                            ?
                            book.getTitle()
                            :
                            "Untitled Book";


            String author =
                    book.getAuthor() != null
                            &&
                    book
                            .getAuthor()
                            .getName() != null
                            ?
                            book
                                    .getAuthor()
                                    .getName()
                            :
                            "Unknown Author";


            String category =
                    book.getCategory() != null
                            &&
                    book
                            .getCategory()
                            .getName() != null
                            ?
                            book
                                    .getCategory()
                                    .getName()
                            :
                            "General";


            // =================================================
            // FIRST OPTION:
            // REAL BOOK CONTENT (EPUB, PDF or HTML)
            // =================================================

            Optional<DigitalResource> bookResource =
                    findExtractableResource(
                            book.getId()
                    );


            if (
                    bookResource.isPresent()
            ) {

                DigitalResource resource =
                        bookResource.get();


                // =============================================
                // IMPORTANT PORTABILITY FIX
                //
                // Do not directly do:
                //
                // new File(resource.getFilePath())
                //
                // because the DB may contain a path belonging
                // to another computer.
                // =============================================

                File bookFile =
                        resolveDigitalResourceFile(
                                resource.getFilePath()
                        );


                if (
                        bookFile != null
                                &&
                        bookFile.exists()
                                &&
                        bookFile.isFile()
                ) {

                    logger.info(
                            "Using real {} content for book {}: {}",
                            documentExtractionService.labelOf(
                                    bookFile.getName()
                            ),
                            book.getId(),
                            bookFile.getAbsolutePath()
                    );


                    List<ExtractedSection>
                            sections =
                            documentExtractionService
                                    .extractSections(
                                            bookFile
                                    );


                    int globalChunkIndex =
                            0;


                    for (
                            ExtractedSection section :
                            sections
                    ) {

                        if (
                                section.getContent() == null
                                        ||
                                section
                                        .getContent()
                                        .isBlank()
                        ) {

                            continue;
                        }


                        String sectionName =
                                section.getTitle() != null
                                        &&
                                !section
                                        .getTitle()
                                        .isBlank()
                                        ?
                                        section.getTitle()
                                        :
                                        "Section "
                                                +
                                        (
                                                section.getOrder()
                                                        +
                                                1
                                        );


                        /*
                         * vector_records.section is a varchar(255) column.
                         * Some EPUBs use a whole sentence (or a converted
                         * PDF filename) as the chapter heading, which makes
                         * the insert fail and aborts the whole book.
                         */
                        sectionName =
                                fitSectionName(
                                        sectionName
                                );


                        List<DocumentChunkerService.Chunk> chunks =
                                documentChunkerService
                                        .chunkTextWithSection(
                                                docId,
                                                title,
                                                author,
                                                category,
                                                "book",
                                                sectionName,
                                                section.getContent(),
                                                globalChunkIndex
                                        );


                        for (
                                DocumentChunkerService.Chunk chunk :
                                chunks
                        ) {

                            chunksCreated++;


                            if (
                                    !force
                                            &&
                                    vectorRecordRepository
                                            .existsByChunkId(
                                                    chunk.getChunkId()
                                            )
                            ) {

                                duplicatesSkipped++;

                                globalChunkIndex++;

                                continue;
                            }


                            String embeddingInput =
                                    title
                                            +
                                    "\n"
                                            +
                                    sectionName
                                            +
                                    "\n"
                                            +
                                    chunk.getContent();


                            try {

                                float[] embedding =
                                        generateEmbeddingWithRetry(
                                                embeddingInput
                                        );


                                embeddingsGenerated++;


                                VectorRecord record =
                                        new VectorRecord(
                                                chunk.getChunkId(),
                                                chunk.getDocumentId(),
                                                "book",
                                                title,
                                                author,
                                                category,
                                                sectionName,
                                                globalChunkIndex,
                                                chunk.getContent(),
                                                objectMapper
                                                        .writeValueAsString(
                                                                embedding
                                                        )
                                        );


                                batchBuffer.add(
                                        record
                                );


                                vectorsStored++;


                            } catch (
                                    DailyEmbeddingQuotaExceededException dailyQuotaError
                            ) {

                                logger.warn(
                                        "Daily embedding quota exhausted. "
                                                +
                                        "Stopping book {} safely at chunk {} "
                                                +
                                        "in section '{}'. Existing vectors are preserved.",
                                        book.getId(),
                                        chunk.getChunkId(),
                                        sectionName
                                );


                                flushBatch(
                                        batchBuffer
                                );


                                stats.put(
                                        "documentsProcessed",
                                        documentsProcessed
                                );


                                stats.put(
                                        "chunksCreated",
                                        chunksCreated
                                );


                                stats.put(
                                        "embeddingsGenerated",
                                        embeddingsGenerated
                                );


                                stats.put(
                                        "vectorsStored",
                                        vectorsStored
                                );


                                stats.put(
                                        "duplicatesSkipped",
                                        duplicatesSkipped
                                );


                                stats.put(
                                        "failedRecords",
                                        failedRecords
                                );


                                return stats;


                            } catch (Exception chunkError) {

                                failedRecords++;


                                logger.error(
                                        "Failed book chunk {} "
                                                +
                                        "in section '{}' "
                                                +
                                        "at index {} "
                                                +
                                        "after retries: {}",
                                        chunk.getChunkId(),
                                        sectionName,
                                        globalChunkIndex,
                                        chunkError.getMessage()
                                );
                            }


                            globalChunkIndex++;


                            if (
                                    batchBuffer.size()
                                            >=
                                    200
                            ) {

                                vectorRecordRepository
                                        .saveAll(
                                                batchBuffer
                                        );


                                batchBuffer.clear();
                            }
                        }
                    }


                    if (
                            chunksCreated == 0
                    ) {

                        throw new IllegalStateException(
                                "No text could be extracted from the "
                                        + documentExtractionService.labelOf(
                                                bookFile.getName()
                                        )
                                        + " of book "
                                        + book.getId()
                                        + ". Sections found: "
                                        + sections.size()
                        );
                    }


                    logger.info(
                            "Real {} ingestion completed for book {}. "
                                    +
                            "Sections: {}, chunks: {}",
                            documentExtractionService.labelOf(
                                    bookFile.getName()
                            ),
                            book.getId(),
                            sections.size(),
                            chunksCreated
                    );


                    stats.put(
                            "documentsProcessed",
                            documentsProcessed
                    );


                    stats.put(
                            "chunksCreated",
                            chunksCreated
                    );


                    stats.put(
                            "embeddingsGenerated",
                            embeddingsGenerated
                    );


                    stats.put(
                            "vectorsStored",
                            vectorsStored
                    );


                    stats.put(
                            "duplicatesSkipped",
                            duplicatesSkipped
                    );


                    stats.put(
                            "failedRecords",
                            failedRecords
                    );


                    return stats;


                } else {

                    /*
                     * A registered file that cannot be read is a real
                     * failure. Falling back to metadata would store
                     * meaningless vectors and report a broken book as
                     * successfully indexed.
                     */
                    throw new IllegalStateException(
                            "Book file for book "
                                    + book.getId()
                                    + " could not be found or is not readable. "
                                    + "Stored path: "
                                    + resource.getFilePath()
                                    + ". Resolved path: "
                                    + (bookFile != null
                                    ? bookFile.getAbsolutePath()
                                    : "null")
                    );
                }
            }


            // =================================================
            // SECOND OPTION:
            // BOOKS.TEXT_CONTENT
            // =================================================

            String text =
                    book.getTextContent();


            if (
                    text != null
                            &&
                    !text.isBlank()
            ) {

                logger.info(
                        "Using books.text_content for book {}",
                        book.getId()
                );


            } else {

                /*
                 * There is no readable book file and no stored full text,
                 * so there is nothing meaningful to embed. Metadata-only
                 * vectors used to be generated here; they made an
                 * unreadable book look indexed while returning no real
                 * answer. Fail instead.
                 */
                throw new IllegalStateException(
                        "Book "
                                + book.getId()
                                + " has no readable EPUB/PDF/HTML resource and no "
                                + "text_content. Refusing to create "
                                + "metadata-only vectors."
                );
            }


            if (
                    text == null
                            ||
                    text.isBlank()
            ) {

                throw new IllegalStateException(
                        "No searchable text could be extracted for book "
                                + book.getId()
                );
            }


            List<DocumentChunkerService.Chunk> chunks =
                    documentChunkerService
                            .chunkText(
                                    docId,
                                    title,
                                    author,
                                    category,
                                    "book",
                                    text
                            );


            int chunkIndex =
                    0;


            for (
                    DocumentChunkerService.Chunk chunk :
                    chunks
            ) {

                chunksCreated++;


                if (
                        !force
                                &&
                        vectorRecordRepository
                                .existsByChunkId(
                                        chunk.getChunkId()
                                )
                ) {

                    duplicatesSkipped++;

                    chunkIndex++;

                    continue;
                }


                float[] embedding =
                        generateEmbeddingWithRetry(
                                title
                                        +
                                "\n"
                                        +
                                chunk.getSection()
                                        +
                                "\n"
                                        +
                                chunk.getContent()
                        );


                embeddingsGenerated++;


                VectorRecord record =
                        new VectorRecord(
                                chunk.getChunkId(),
                                chunk.getDocumentId(),
                                "book",
                                title,
                                author,
                                category,
                                chunk.getSection(),
                                chunkIndex,
                                chunk.getContent(),
                                objectMapper
                                        .writeValueAsString(
                                                embedding
                                        )
                        );


                batchBuffer.add(
                        record
                );


                vectorsStored++;

                chunkIndex++;


                if (
                        batchBuffer.size()
                                >=
                        200
                ) {

                    vectorRecordRepository
                            .saveAll(
                                    batchBuffer
                            );


                    batchBuffer.clear();
                }
            }


        } catch (
                DailyEmbeddingQuotaExceededException e
        ) {            logger.warn(
                    "Daily embedding quota exhausted while processing book {}. Existing vectors are preserved.",
                    book.getId()
            );


            flushBatch(
                    batchBuffer
            );


            // Resume later from the chunks that are still missing.
            throw e;

        } catch (Exception e) {

            failedRecords++;


            logger.error(
                    "Book ingestion failed for ID {}: {}",
                    book.getId(),
                    e.getMessage(),
                    e
            );


            /*
             * Structural failures (missing/unreadable EPUB, unreadable
             * archive, database error) must reach the caller so they can
             * be reported. Chunk-level failures are already counted in
             * failedRecords and do not stop the run.
             */
            throw e instanceof IllegalStateException
                    ? (IllegalStateException) e
                    : new IllegalStateException(
                            "Book ingestion failed for ID "
                                    + book.getId()
                                    + ": "
                                    + e.getMessage(),
                            e
                    );
        }


        stats.put(
                "documentsProcessed",
                documentsProcessed
        );


        stats.put(
                "chunksCreated",
                chunksCreated
        );


        stats.put(
                "embeddingsGenerated",
                embeddingsGenerated
        );


        stats.put(
                "vectorsStored",
                vectorsStored
        );


        stats.put(
                "duplicatesSkipped",
                duplicatesSkipped
        );


        stats.put(
                "failedRecords",
                failedRecords
        );


        return stats;
    }


    // =========================================================
    // EMBEDDING WITH PACING + RETRY
    // =========================================================

    private synchronized float[] generateEmbeddingWithRetry(
            String input
    ) {

        int retryCount =
                0;


        while (
                true
        ) {

            try {

                paceEmbeddingRequest();


                return embeddingService
                        .generateEmbedding(
                                input
                        );


            } catch (Exception e) {

                // =============================================
                // DAILY QUOTA
                // =============================================

                if (
                        isDailyQuotaError(
                                e
                        )
                ) {

                    throw new DailyEmbeddingQuotaExceededException(
                            "Daily Gemini embedding quota exhausted.",
                            e
                    );
                }


                // =============================================
                // TEMPORARY RATE LIMIT
                // =============================================

                if (
                        isRateLimitError(
                                e
                        )
                                &&
                        retryCount
                                <
                        EMBEDDING_MAX_RETRIES
                ) {

                    long retryDelay =
                            Math.min(
                                    EMBEDDING_INITIAL_RETRY_DELAY_MS
                                            *
                                    (
                                            1L
                                                    <<
                                            retryCount
                                    ),
                                    EMBEDDING_MAX_RETRY_DELAY_MS
                            );


                    retryCount++;


                    logger.warn(
                            "Embedding API rate limited. "
                                    +
                            "Retry {}/{} in {} ms.",
                            retryCount,
                            EMBEDDING_MAX_RETRIES,
                            retryDelay
                    );


                    sleepQuietly(
                            retryDelay
                    );


                    continue;
                }


                if (
                        e instanceof RuntimeException
                ) {

                    throw (RuntimeException) e;
                }


                throw new IllegalStateException(
                        "Embedding generation failed",
                        e
                );
            }
        }
    }


    // =========================================================
    // PACING
    // =========================================================

    private void paceEmbeddingRequest() {

        if ("local".equalsIgnoreCase(embeddingProvider)) {
            return;
        }

        long now =
                System.currentTimeMillis();


        long elapsed =
                now
                        -
                lastEmbeddingRequestAt;


        long waitTime =
                EMBEDDING_MIN_INTERVAL_MS
                        -
                elapsed;


        if (
                waitTime > 0
        ) {

            sleepQuietly(
                    waitTime
            );
        }


        lastEmbeddingRequestAt =
                System.currentTimeMillis();
    }


    // =========================================================
    // DAILY QUOTA DETECTION
    // =========================================================

    private boolean isDailyQuotaError(
            Exception e
    ) {

        Throwable current =
                e;


        while (
                current != null
        ) {

            String message =
                    current.getMessage();


            if (
                    message != null
            ) {

                String lower =
                        message.toLowerCase(
                                Locale.ROOT
                        );


                if (
                        lower.contains(
                                "embedcontentrequestsperday"
                        )
                                ||
                        lower.contains(
                                "requestsperday"
                        )
                                ||
                        lower.contains(
                                "perdayperuser"
                        )
                                ||
                        lower.contains(
                                "per day per user"
                        )
                                ||
                        lower.contains(
                                "requests per day"
                        )
                                ||
                        lower.contains(
                                "daily quota"
                        )
                ) {

                    return true;
                }
            }


            current =
                    current.getCause();
        }


        return false;
    }


    // =========================================================
    // DAILY QUOTA EXCEPTION
    // =========================================================

    private static class DailyEmbeddingQuotaExceededException
            extends RuntimeException {

        public DailyEmbeddingQuotaExceededException(
                String message,
                Throwable cause
        ) {

            super(
                    message,
                    cause
            );
        }
    }


    // =========================================================
    // RATE LIMIT DETECTION
    // =========================================================

    private boolean isRateLimitError(
            Exception e
    ) {

        Throwable current =
                e;


        while (
                current != null
        ) {

            String message =
                    current.getMessage();


            if (
                    message != null
            ) {

                String lower =
                        message.toLowerCase(
                                Locale.ROOT
                        );


                if (
                        lower.contains(
                                "http 429"
                        )
                                ||
                        lower.contains(
                                "resource_exhausted"
                        )
                                ||
                        lower.contains(
                                "quota exceeded"
                        )
                                ||
                        lower.contains(
                                "rate limit"
                        )
                ) {

                    return true;
                }
            }


            current =
                    current.getCause();
        }


        return false;
    }


    // =========================================================
    // SAFE SLEEP
    // =========================================================

    private void sleepQuietly(
            long milliseconds
    ) {

        try {

            Thread.sleep(
                    milliseconds
            );


        } catch (
                InterruptedException e
        ) {

            Thread
                    .currentThread()
                    .interrupt();


            throw new IllegalStateException(
                    "Embedding ingestion interrupted while waiting",
                    e
            );
        }
    }


    // =========================================================
    // FIND A READABLE RESOURCE FOR BOOK
    // =========================================================

    private Optional<DigitalResource> findExtractableResource(
            Long bookId
    ) {

        List<DigitalResource> resources =
                digitalResourceRepository
                        .findByBook_Id(
                                bookId
                        );


        for (
                DigitalResource resource :
                resources
        ) {

            if (
                    resource == null
            ) {

                continue;
            }


            String fileName =
                    resource.getFileName();


            String format =
                    resource.getFormat();


            if (
                    documentExtractionService.supports(
                            fileName,
                            format
                    )
            ) {

                return Optional.of(
                        resource
                );
            }
        }


        return Optional.empty();
    }


    // =========================================================
    // BOOK DOCUMENT ID
    // =========================================================

    private static String fitSectionName(
            String name
    ) {

        final int maxLength = 250;


        if (
                name == null
                        ||
                name.isBlank()
        ) {

            return "Section";
        }


        String trimmed =
                name.trim();


        if (
                trimmed.length()
                        <=
                maxLength
        ) {

            return trimmed;
        }


        return trimmed.substring(
                0,
                maxLength - 3
        ) + "...";
    }


    private String getBookDocumentId(
            Book book
    ) {

        if (
                book.getResourceId() != null
                        &&
                !book
                        .getResourceId()
                        .isBlank()
        ) {

            return book
                    .getResourceId()
                    .trim();
        }


        return "BOOK-ID-"
                +
        book.getId();
    }


    // =========================================================
    // SAVE REMAINING BATCH
    // =========================================================

    private void flushBatch(
            List<VectorRecord> batchBuffer
    ) {

        if (
                batchBuffer == null
                        ||
                batchBuffer.isEmpty()
        ) {

            return;
        }


        vectorRecordRepository
                .saveAll(
                        batchBuffer
                );


        batchBuffer.clear();
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
    // RESOLVE NORMAL DATA FILE
    // =========================================================

    private File resolveFile(
            String relativePath
    ) {

        if (
                relativePath == null
                        ||
                relativePath.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "AstraLib data file path cannot be empty."
            );
        }


        String portablePath =
                relativePath
                        .trim()
                        .replace(
                                "\\",
                                File.separator
                        )
                        .replace(
                                "/",
                                File.separator
                        );


        return getDataRootPath()
                .resolve(
                        portablePath
                )
                .normalize()
                .toFile();
    }


    // =========================================================
    // RESOLVE DIGITAL RESOURCE FILE
    // =========================================================
    //
    // This solves old database paths such as:
    //
    // <old-computer>\AstraLib\data\
    // fulltext\books\sherlock_holmes\sherlock_holmes.epub
    //
    // On another computer it automatically becomes:
    //
    // <their AstraLib>\data\fulltext\books\...
    //
    // Preferred future DB format:
    //
    // fulltext/books/sherlock_holmes/sherlock_holmes.epub
    //
    // =========================================================

    private File resolveDigitalResourceFile(
            String storedPath
    ) {

        if (
                storedPath == null
                        ||
                storedPath.isBlank()
        ) {

            return null;
        }


        String cleaned =
                storedPath.trim();


        // =====================================================
        // 1. Existing absolute path on this computer
        // =====================================================

        try {

            Path rawPath =
                    Paths.get(
                            cleaned
                    );


            if (
                    rawPath.isAbsolute()
                            &&
                    rawPath
                            .toFile()
                            .exists()
            ) {

                return rawPath
                        .toAbsolutePath()
                        .normalize()
                        .toFile();
            }


        } catch (Exception ignored) {

            // Continue using portable resolution.
        }


        // =====================================================
        // 2. NORMALIZE SEPARATORS
        // =====================================================

        String normalizedText =
                cleaned.replace(
                        "\\",
                        "/"
                );


        String lower =
                normalizedText.toLowerCase(
                        Locale.ROOT
                );


        // =====================================================
        // 3. OLD ABSOLUTE ASTRALIB PATH
        //
        // Example:
        //
        // <old-machine>/AstraLib/data/fulltext/...
        //
        // Keep only:
        //
        // fulltext/...
        // =====================================================

        String dataMarker =
                "/data/";


        int dataIndex =
                lower.indexOf(
                        dataMarker
                );


        if (
                dataIndex >= 0
        ) {

            String relativeToData =
                    normalizedText.substring(
                            dataIndex
                                    +
                            dataMarker.length()
                    );


            return resolveInsideRoot(
                    getDataRootPath(),
                    relativeToData,
                    storedPath
            );
        }


        // =====================================================
        // 3b. OLD ABSOLUTE UPLOAD PATH
        //
        // Example:
        //
        // <machine>/AstraLib/astralib-backend/uploads/file.epub
        //
        // Keep only:
        //
        // file.epub
        // =====================================================

        String uploadsMarker =
                "/uploads/";


        int uploadsIndex =
                lower.indexOf(
                        uploadsMarker
                );


        if (
                uploadsIndex >= 0
        ) {

            return resolveInsideRoot(
                    getUploadRootPath(),
                    normalizedText.substring(
                            uploadsIndex
                                    +
                            uploadsMarker.length()
                    ),
                    storedPath
            );
        }


        // =====================================================
        // 4. PATH STARTING WITH data/
        // =====================================================

        if (
                lower.startsWith(
                        "data/"
                )
        ) {

            normalizedText =
                    normalizedText.substring(
                            "data/".length()
                    );
        }


        // =====================================================
        // 4b. PATH STARTING WITH uploads/
        //
        // Files saved by ResourceController live under the upload
        // root, not under the data root.
        // =====================================================

        if (
                lower.startsWith(
                        "uploads/"
                )
        ) {

            return resolveInsideRoot(
                    getUploadRootPath(),
                    normalizedText.substring(
                            "uploads/".length()
                    ),
                    storedPath
            );
        }


        // =====================================================
        // 5. PREFERRED PORTABLE PATH
        //
        // fulltext/books/...
        // =====================================================

        return resolveInsideRoot(
                getDataRootPath(),
                normalizedText,
                storedPath
        );
    }


    // =========================================================
    // UPLOAD ROOT
    // =========================================================

    private Path getUploadRootPath() {

        return Paths.get(
                        uploadRoot
                )
                .toAbsolutePath()
                .normalize();
    }


    // =========================================================
    // RESOLVE INSIDE A STORAGE ROOT
    //
    // Keeps stored paths from escaping the configured roots
    // through ".." segments or an absolute path.
    // =========================================================

    private File resolveInsideRoot(
            Path root,
            String relativePath,
            String storedPath
    ) {

        if (
                relativePath == null
                        ||
                relativePath.isBlank()
        ) {

            throw new IllegalStateException(
                    "Empty file path stored for digital resource: "
                            + storedPath
            );
        }


        Path resolved =
                root
                        .resolve(
                                relativePath
                                        .replace(
                                                "\\",
                                                "/"
                                        )
                                        .replace(
                                                "/",
                                                File.separator
                                        )
                        )
                        .normalize();


        if (
                !resolved.startsWith(
                        root
                )
        ) {

            throw new IllegalStateException(
                    "Refusing to resolve a digital resource path "
                            + "outside its storage root. Root: "
                            + root
                            + " Stored path: "
                            + storedPath
            );
        }


        return resolved.toFile();
    }
}
