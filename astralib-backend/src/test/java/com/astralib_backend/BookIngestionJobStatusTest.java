package com.astralib_backend;

import com.astralib.backend.BookIngestionJobService;
import com.astralib.backend.VectorIngestionService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Status semantics for background ingestion jobs.
 *
 * A book must never be reported as fully indexed when chunks failed or
 * when nothing was stored, so these cover each outcome deterministically.
 */
class BookIngestionJobStatusTest {

    private VectorIngestionService ingestionService;
    private BookIngestionJobService jobService;

    @BeforeEach
    void setUp() {
        ingestionService = mock(VectorIngestionService.class);
        jobService = new BookIngestionJobService(ingestionService);
    }

    @AfterEach
    void tearDown() {
        jobService.shutdown();
    }

    private Map<String, Object> runJob() throws InterruptedException {

        Map<String, Object> submitted = jobService.submit(7L, "Test Book");
        String jobId = (String) submitted.get("jobId");

        long deadline = System.currentTimeMillis() + 5000;

        while (System.currentTimeMillis() < deadline) {

            Map<String, Object> status = jobService.status(jobId);

            assertNotNull(status);

            if (Boolean.TRUE.equals(status.get("finished"))) {
                return status;
            }

            Thread.sleep(20);
        }

        fail("Ingestion job did not finish in time");
        return null;
    }

    @Test
    void completedWhenEveryChunkWasEmbedded() throws Exception {

        when(ingestionService.ingestBookVectors(7L)).thenReturn(Map.of(
                "vectorsStored", 12,
                "duplicatesSkipped", 0,
                "failedRecords", 0,
                "vectorRecordsForBook", 12
        ));

        Map<String, Object> status = runJob();

        assertEquals("COMPLETED", status.get("status"));
        assertTrue(((String) status.get("message")).contains("12"));
    }

    @Test
    void partialWhenSomeChunksFailed() throws Exception {

        when(ingestionService.ingestBookVectors(7L)).thenReturn(Map.of(
                "vectorsStored", 9,
                "duplicatesSkipped", 0,
                "failedRecords", 3,
                "vectorRecordsForBook", 9
        ));

        Map<String, Object> status = runJob();

        assertEquals(
                "PARTIAL",
                status.get("status"),
                "Failed chunks must never be reported as a complete index"
        );

        assertTrue(((String) status.get("message")).contains("3"));
    }

    @Test
    void completedWhenTheBookWasAlreadyIndexed() throws Exception {

        when(ingestionService.ingestBookVectors(7L)).thenReturn(Map.of(
                "vectorsStored", 0,
                "duplicatesSkipped", 40,
                "failedRecords", 0,
                "vectorRecordsForBook", 40
        ));

        Map<String, Object> status = runJob();

        assertEquals("COMPLETED", status.get("status"));
        assertTrue(((String) status.get("message")).contains("already indexed"));
    }

    @Test
    void failedWhenNothingIsStored() throws Exception {

        when(ingestionService.ingestBookVectors(7L)).thenReturn(Map.of(
                "vectorsStored", 0,
                "duplicatesSkipped", 0,
                "failedRecords", 0,
                "vectorRecordsForBook", 0
        ));

        Map<String, Object> status = runJob();

        assertEquals("FAILED", status.get("status"));
    }

    @Test
    void failedWhenIngestionThrows() throws Exception {

        when(ingestionService.ingestBookVectors(7L))
                .thenThrow(new IllegalStateException("EPUB file could not be found"));

        Map<String, Object> status = runJob();

        assertEquals("FAILED", status.get("status"));
        assertEquals("EPUB file could not be found", status.get("message"));
    }
}
