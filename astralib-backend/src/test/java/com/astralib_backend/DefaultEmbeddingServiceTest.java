package com.astralib_backend;

import com.astralib.backend.DefaultEmbeddingService;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deterministic tests for the embedding HTTP contract.
 *
 * A stub HTTP server stands in for the Python FastAPI service, so these
 * tests run offline, need no model, and never touch the network beyond
 * the loopback interface.
 */
class DefaultEmbeddingServiceTest {

    private static final int DIMENSION = 384;

    private HttpServer server;

    private final AtomicInteger requestCount = new AtomicInteger();

    private volatile int responseStatus;
    private volatile String responseBody;
    private volatile String lastRequestBody;

    private String embeddingUrl;

    @BeforeEach
    void startStubService() throws Exception {

        responseStatus = 200;
        responseBody = embeddingJson(DIMENSION);

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);

        server.createContext("/embed", exchange -> {
            requestCount.incrementAndGet();

            try (InputStream in = exchange.getRequestBody()) {
                lastRequestBody = new String(
                        in.readAllBytes(),
                        StandardCharsets.UTF_8
                );
            }

            byte[] payload = responseBody.getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().add(
                    "Content-Type",
                    "application/json"
            );
            exchange.sendResponseHeaders(responseStatus, payload.length);

            try (OutputStream out = exchange.getResponseBody()) {
                out.write(payload);
            }
        });

        server.start();

        embeddingUrl = "http://127.0.0.1:"
                + server.getAddress().getPort()
                + "/embed";
    }

    @AfterEach
    void stopStubService() {
        if (server != null) {
            server.stop(0);
        }
    }

    private DefaultEmbeddingService serviceUsing(String url, int dimension) {

        DefaultEmbeddingService service = new DefaultEmbeddingService();

        ReflectionTestUtils.setField(service, "provider", "local");
        ReflectionTestUtils.setField(service, "modelName", "all-MiniLM-L6-v2");
        ReflectionTestUtils.setField(service, "dimension", dimension);
        ReflectionTestUtils.setField(service, "apiKey", "");
        ReflectionTestUtils.setField(service, "localEmbeddingUrl", url);

        return service;
    }

    private static String embeddingJson(int dimension) {

        String values = IntStream.range(0, dimension)
                .mapToObj(index -> String.valueOf(index / (double) dimension))
                .collect(Collectors.joining(","));

        return "{\"embedding\":[" + values + "]}";
    }

    @Test
    void sendsTheTextAndParsesA384DimensionEmbedding() {

        DefaultEmbeddingService service =
                serviceUsing(embeddingUrl, DIMENSION);

        float[] embedding = service.generateEmbedding("Semantic search sample");

        assertEquals(DIMENSION, embedding.length);
        assertEquals(1, requestCount.get());

        assertTrue(
                lastRequestBody.contains("Semantic search sample"),
                "The request body should carry the text to embed"
        );

        for (float value : embedding) {
            assertTrue(
                    Float.isFinite(value),
                    "Embedding values must be finite numbers"
            );
        }
    }

    @Test
    void rejectsAnEmbeddingWithTheWrongDimension() {

        responseBody = embeddingJson(3);

        DefaultEmbeddingService service =
                serviceUsing(embeddingUrl, DIMENSION);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.generateEmbedding("Wrong size")
        );

        assertTrue(
                error.getMessage().contains("dimension mismatch"),
                "Unexpected message: " + error.getMessage()
        );
    }

    @Test
    void reportsNonSuccessfulHttpResponses() {

        responseStatus = 503;
        responseBody = "{\"detail\":\"model loading\"}";

        DefaultEmbeddingService service =
                serviceUsing(embeddingUrl, DIMENSION);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.generateEmbedding("Service unavailable")
        );

        assertTrue(
                error.getMessage().contains("503"),
                "Unexpected message: " + error.getMessage()
        );
    }

    @Test
    void blankTextShortCircuitsWithoutCallingTheService() {

        DefaultEmbeddingService service =
                serviceUsing(embeddingUrl, DIMENSION);

        float[] embedding = service.generateEmbedding("   ");

        assertEquals(0, requestCount.get());
        assertArrayEquals(new float[DIMENSION], embedding);
    }
}
