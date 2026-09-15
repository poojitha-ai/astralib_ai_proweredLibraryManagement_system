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


    private final ObjectMapper objectMapper =
            new ObjectMapper();


    // =========================================================
    // EMBEDDING RATE-LIMIT PROTECTION
    // =========================================================

    private static final long EMBEDDING_MIN_INTERVAL_MS =
            700L;

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
            // REAL EPUB CONTENT
            // =================================================

            Optional<DigitalResource> epubResource =
                    findEpubResource(
                            book.getId()
                    );


            if (
                    epubResource.isPresent()
            ) {

                DigitalResource resource =
                        epubResource.get();


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

                File epubFile =
                        resolveDigitalResourceFile(
                                resource.getFilePath()
                        );


                if (
                        epubFile != null
                                &&
                        epubFile.exists()
                                &&
                        epubFile.isFile()
                ) {

                    logger.info(
                            "Using real EPUB content for book {}: {}",
                            book.getId(),
                            epubFile.getAbsolutePath()
                    );


                    List<EpubTextExtractionService.EpubSection>
                            sections =
                            epubTextExtractionService
                                    .extractSections(
                                            epubFile
                                    );


                    int globalChunkIndex =
                            0;


                    for (
                            EpubTextExtractionService.EpubSection section :
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
                                        "Failed EPUB chunk {} "
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


                    logger.info(
                            "Real EPUB ingestion completed for book {}. "
                                    +
                            "Sections: {}, chunks: {}",
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

                    logger.warn(
                            "EPUB resource is registered but the file could not be found for book {}. Stored path: {}. Resolved path: {}",
                            book.getId(),
                            resource.getFilePath(),
                            epubFile != null
                                    ?
                                    epubFile.getAbsolutePath()
                                    :
                                    "null"
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

                // =============================================
                // FINAL FALLBACK:
                // METADATA ONLY
                // =============================================

                logger.info(
                        "No EPUB/full text for book {}. "
                                +
                        "Using metadata fallback.",
                        book.getId()
                );


                text =
                        textPreprocessingService
                                .generateBookTextContent(
                                        book
                                );
            }


            if (
                    text == null
                            ||
                    text.isBlank()
            ) {

                logger.warn(
                        "No searchable text available for book {}",
                        book.getId()
                );


                stats.put(
                        "documentsProcessed",
                        documentsProcessed
                );


                stats.put(
                        "chunksCreated",
                        0
                );


                stats.put(
                        "embeddingsGenerated",
                        0
                );


                stats.put(
                        "vectorsStored",
                        0
                );


                stats.put(
                        "duplicatesSkipped",
                        0
                );


                stats.put(
                        "failedRecords",
                        1
                );


                return stats;
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
        ) {

            logger.warn(
                    "Daily embedding quota exhausted while processing book {}. Existing vectors are preserved.",
                    book.getId()
            );


            flushBatch(
                    batchBuffer
            );


        } catch (Exception e) {

            failedRecords++;


            logger.error(
                    "Book ingestion failed for ID {}: {}",
                    book.getId(),
                    e.getMessage(),
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
    // FIND EPUB RESOURCE FOR BOOK
    // =========================================================

    private Optional<DigitalResource> findEpubResource(
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


            boolean epubByName =
                    fileName != null
                            &&
                    fileName
                            .toLowerCase(
                                    Locale.ROOT
                            )
                            .endsWith(
                                    ".epub"
                            );


            boolean epubByFormat =
                    format != null
                            &&
                    format.equalsIgnoreCase(
                            "application/epub+zip"
                    );


            if (
                    epubByName
                            ||
                    epubByFormat
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


            return getDataRootPath()
                    .resolve(
                            relativeToData.replace(
                                    "/",
                                    File.separator
                            )
                    )
                    .normalize()
                    .toFile();
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
        // 5. PREFERRED PORTABLE PATH
        //
        // fulltext/books/...
        // =====================================================

        return getDataRootPath()
                .resolve(
                        normalizedText.replace(
                                "/",
                                File.separator
                        )
                )
                .normalize()
                .toFile();
    }
}
