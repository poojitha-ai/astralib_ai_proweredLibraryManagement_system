package com.astralib_backend;

import com.astralib.backend.DefaultEmbeddingService;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Live integration test for the local Python embedding service
 * (local-embeddings/app.py, sentence-transformers/all-MiniLM-L6-v2).
 *
 * This is the one test that needs an external process, so it is:
 *  - tagged "live-embedding" (run only with -Dgroups=live-embedding),
 *  - skipped automatically when the service is not listening,
 *  - free of any Spring context, so it can never touch a database.
 *
 * The deterministic HTTP contract is covered separately by
 * DefaultEmbeddingServiceTest, which needs no running service.
 */
@Tag("live-embedding")
class EmbeddingServiceIntegrationTest {

    private static final String LOCAL_EMBEDDING_URL =
            System.getenv().getOrDefault(
                    "LLM_LOCAL_EMBEDDING_URL",
                    "http://127.0.0.1:8001/embed"
            );

    @Test
    void shouldGenerateLocalEmbedding() {

        assumeTrue(
                isListening(LOCAL_EMBEDDING_URL),
                "Skipped: no local embedding service at "
                        + LOCAL_EMBEDDING_URL
                        + " (start local-embeddings/app.py to run this test)"
        );

        DefaultEmbeddingService service = new DefaultEmbeddingService();

        ReflectionTestUtils.setField(service, "provider", "local");
        ReflectionTestUtils.setField(service, "dimension", 384);
        ReflectionTestUtils.setField(service, "apiKey", "");
        ReflectionTestUtils.setField(
                service,
                "localEmbeddingUrl",
                LOCAL_EMBEDDING_URL
        );

        float[] embedding = service.generateEmbedding(
                "AstraLib is an AI-powered smart library."
        );

        assertNotNull(embedding);
        assertEquals(384, embedding.length);

        for (float value : embedding) {
            assertTrue(Float.isFinite(value));
        }
    }

    private static boolean isListening(String url) {

        try {
            URI uri = URI.create(url);

            int port = uri.getPort() > 0
                    ? uri.getPort()
                    : 80;

            try (Socket socket = new Socket()) {
                socket.connect(
                        new InetSocketAddress(uri.getHost(), port),
                        500
                );
                return true;
            }

        } catch (Exception notRunning) {
            return false;
        }
    }
}
