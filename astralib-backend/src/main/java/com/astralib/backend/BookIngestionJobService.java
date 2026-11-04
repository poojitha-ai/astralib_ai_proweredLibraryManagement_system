package com.astralib.backend;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Runs one-book vector ingestion in the background and exposes its status.
 *
 * Ingestion of a real book makes hundreds of embedding calls, so it cannot
 * run inside the import HTTP request. The import returns immediately with a
 * job id, and the UI polls the status endpoint. Jobs are kept in memory
 * (single-instance application) and the last 50 are retained.
 */
@Service
public class BookIngestionJobService {

    public static final String QUEUED = "QUEUED";
    public static final String RUNNING = "RUNNING";
    public static final String COMPLETED = "COMPLETED";
    public static final String PARTIAL = "PARTIAL";
    public static final String FAILED = "FAILED";

    private static final int MAX_JOBS = 50;

    private static final Logger logger =
            LoggerFactory.getLogger(
                    BookIngestionJobService.class
            );

    private final VectorIngestionService vectorIngestionService;

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(
                        runnable,
                        "astralib-book-ingestion"
                );
                thread.setDaemon(true);
                return thread;
            });

    private final Map<String, IngestionJob> jobs =
            Collections.synchronizedMap(
                    new LinkedHashMap<>() {
                        @Override
                        protected boolean removeEldestEntry(
                                Map.Entry<String, IngestionJob> eldest) {
                            return size() > MAX_JOBS;
                        }
                    }
            );

    public BookIngestionJobService(
            VectorIngestionService vectorIngestionService) {

        this.vectorIngestionService = vectorIngestionService;
    }

    // =========================================================
    // SUBMIT
    // =========================================================

    public Map<String, Object> submit(
            Long bookId,
            String title) {

        IngestionJob job =
                new IngestionJob(
                        UUID.randomUUID().toString(),
                        bookId,
                        title
                );

        jobs.put(
                job.jobId,
                job
        );

        executor.submit(
                () -> run(job)
        );

        logger.info(
                "Queued embedding job {} for book {}",
                job.jobId,
                bookId
        );

        return job.toMap();
    }

    // =========================================================
    // STATUS
    // =========================================================

    public Map<String, Object> status(
            String jobId) {

        IngestionJob job =
                jobs.get(
                        jobId
                );

        return job == null
                ? null
                : job.toMap();
    }

    public Map<String, Object> latestForBook(
            Long bookId) {

        synchronized (jobs) {

            IngestionJob newest = null;

            for (IngestionJob job : jobs.values()) {

                if (
                        belongsToBook(
                                job,
                                bookId
                        )
                ) {

                    newest = job;
                }
            }

            return newest == null
                    ? null
                    : newest.toMap();
        }
    }

    private static boolean belongsToBook(
            IngestionJob job,
            Long bookId) {

        return job.bookId != null
                && job.bookId.equals(bookId);
    }

    // =========================================================
    // EXECUTION
    // =========================================================

    private void run(
            IngestionJob job) {

        job.status = RUNNING;
        job.startedAt = Instant.now();

        try {

            Map<String, Object> stats =
                    vectorIngestionService
                            .ingestBookVectors(
                                    job.bookId
                            );

            job.stats = new LinkedHashMap<>(stats);

            int stored = asInt(stats.get("vectorsStored"));
            int duplicates = asInt(stats.get("duplicatesSkipped"));
            int failed = asInt(stats.get("failedRecords"));
            int totalForBook = asInt(stats.get("vectorRecordsForBook"));

            if (failed > 0) {

                job.status = PARTIAL;

                job.message =
                        failed
                                + " chunk(s) failed to embed. "
                                + stored
                                + " chunk(s) stored. Re-run the ingestion "
                                + "for this book to fill the gaps.";

            } else if (totalForBook <= 0) {

                job.status = FAILED;

                job.message =
                        "No vector records exist for this book after "
                                + "ingestion.";

            } else if (stored == 0 && duplicates > 0) {

                job.status = COMPLETED;

                job.message =
                        "Book was already indexed. "
                                + duplicates
                                + " existing chunk(s) were kept unchanged.";

            } else {

                job.status = COMPLETED;

                job.message =
                        stored
                                + " chunk(s) embedded and stored from "
                                + "the book's own text.";
            }

        } catch (Exception e) {

            job.status = FAILED;
            job.message = e.getMessage() != null
                    ? e.getMessage()
                    : e.getClass().getSimpleName();

            logger.error(
                    "Embedding job {} for book {} failed: {}",
                    job.jobId,
                    job.bookId,
                    job.message,
                    e
            );

        } finally {

            job.finishedAt = Instant.now();

            logger.info(
                    "Embedding job {} for book {} finished with status {}",
                    job.jobId,
                    job.bookId,
                    job.status
            );
        }
    }

    private static int asInt(
            Object value) {

        return value instanceof Number
                ? ((Number) value).intValue()
                : 0;
    }

    @PreDestroy
    public void shutdown() {

        executor.shutdown();

        try {

            if (
                    !executor.awaitTermination(
                            5,
                            TimeUnit.SECONDS
                    )
            ) {

                executor.shutdownNow();
            }

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    // =========================================================
    // JOB
    // =========================================================

    private static final class IngestionJob {

        private final String jobId;
        private final Long bookId;
        private final String title;
        private final Instant submittedAt = Instant.now();

        private volatile String status = QUEUED;
        private volatile String message = "Waiting to start.";
        private volatile Instant startedAt;
        private volatile Instant finishedAt;
        private volatile Map<String, Object> stats = Map.of();

        private IngestionJob(
                String jobId,
                Long bookId,
                String title) {

            this.jobId = jobId;
            this.bookId = bookId;
            this.title = title;
        }

        private Map<String, Object> toMap() {

            Map<String, Object> result =
                    new LinkedHashMap<>();

            result.put("jobId", jobId);
            result.put("bookId", bookId);
            result.put("title", title);
            result.put("status", status);
            result.put("message", message);
            result.put("finished", finishedAt != null);
            result.put("submittedAt", submittedAt.toString());
            result.put(
                    "startedAt",
                    startedAt != null ? startedAt.toString() : null
            );
            result.put(
                    "finishedAt",
                    finishedAt != null ? finishedAt.toString() : null
            );
            result.put("stats", stats);

            return result;
        }
    }
}
