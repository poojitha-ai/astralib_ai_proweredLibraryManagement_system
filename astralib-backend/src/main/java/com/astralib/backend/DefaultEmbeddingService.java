package com.astralib.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DefaultEmbeddingService implements EmbeddingService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Value("${llm.embedding-provider:none}")
    private String provider;

    @Value("${llm.embedding-model:all-MiniLM-L6-v2}")
    private String modelName;

    @Value("${llm.embedding-dimension:384}")
    private int dimension;

    @Value("${llm.api-key:}")
    private String apiKey;

    @Value("${llm.local-embedding-url:http://127.0.0.1:8001/embed}")
    private String localEmbeddingUrl;

    @Override
    public int getDimension() {
        return dimension;
    }

    @Override
    public float[] generateEmbedding(String text) {

        if (text == null || text.isBlank()) {
            return new float[dimension];
        }

        if ("local".equalsIgnoreCase(provider)) {
            return generateLocalEmbedding(text);
        }

        if ("gemini".equalsIgnoreCase(provider)) {
            return generateGeminiEmbedding(text);
        }

        throw new IllegalStateException(
                "Unknown embedding provider: " + provider
                        + ". Set LLM_EMBEDDING_PROVIDER to local or gemini."
        );
    }

    private float[] generateLocalEmbedding(String text) {

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("text", text);

            String jsonBody = objectMapper.writeValueAsString(body);

            // Send the request as explicit UTF-8 bytes.
            byte[] requestBytes =
                    jsonBody.getBytes(StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(localEmbeddingUrl))
                    .timeout(Duration.ofSeconds(60))
                    .header(
                            "Content-Type",
                            "application/json; charset=UTF-8"
                    )
                    .header("Accept", "application/json")
                    .POST(
                            HttpRequest.BodyPublishers.ofByteArray(
                                    requestBytes
                            )
                    )
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(
                            StandardCharsets.UTF_8
                    )
            );

            if (response.statusCode() < 200
                    || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "Local embedding service returned HTTP "
                                + response.statusCode() + ": "
                                + response.body()
                );
            }

            JsonNode root = objectMapper.readTree(response.body());

            // Expected response: {"embedding": [0.1, 0.2, ...]}
            JsonNode values = root.path("embedding");

            if (!values.isArray()) {
                throw new IllegalStateException(
                        "Local embedding response must contain an "
                                + "'embedding' array. Response: "
                                + response.body()
                );
            }

            return convertToFloatArray(values, "local");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "Local embedding request was interrupted.",
                    e
            );

        } catch (Exception e) {
            if (e instanceof IllegalStateException) {
                throw (IllegalStateException) e;
            }

            throw new IllegalStateException(
                    "Local embedding request failed. Check that the "
                            + "Python service is running at "
                            + localEmbeddingUrl,
                    e
            );
        }
    }

    private float[] generateGeminiEmbedding(String text) {

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "Gemini API key is missing. Set LLM_API_KEY."
            );
        }

        try {
            String endpoint =
                    "https://generativelanguage.googleapis.com/v1beta/models/"
                            + modelName + ":embedContent";

            Map<String, Object> textPart = new LinkedHashMap<>();
            textPart.put("text", text);

            Map<String, Object> content = new LinkedHashMap<>();
            content.put("parts", List.of(textPart));

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", "models/" + modelName);
            body.put("content", content);
            body.put("outputDimensionality", dimension);

            String jsonBody = objectMapper.writeValueAsString(body);

            byte[] requestBytes =
                    jsonBody.getBytes(StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(60))
                    .header(
                            "Content-Type",
                            "application/json; charset=UTF-8"
                    )
                    .header("Accept", "application/json")
                    .header("x-goog-api-key", apiKey)
                    .POST(
                            HttpRequest.BodyPublishers.ofByteArray(
                                    requestBytes
                            )
                    )
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(
                            StandardCharsets.UTF_8
                    )
            );

            if (response.statusCode() < 200
                    || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "Gemini embedding API returned HTTP "
                                + response.statusCode() + ": "
                                + response.body()
                );
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode values = root.path("embedding").path("values");

            if (!values.isArray()) {
                throw new IllegalStateException(
                        "Gemini response did not contain embedding.values"
                );
            }

            return convertToFloatArray(values, "Gemini");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "Gemini embedding request was interrupted.",
                    e
            );

        } catch (Exception e) {
            if (e instanceof IllegalStateException) {
                throw (IllegalStateException) e;
            }

            throw new IllegalStateException(
                    "Gemini embedding request failed.",
                    e
            );
        }
    }

    private float[] convertToFloatArray(
            JsonNode values,
            String source) {

        if (values.size() != dimension) {
            throw new IllegalStateException(
                    source + " embedding dimension mismatch. Expected "
                            + dimension + " but received " + values.size()
            );
        }

        float[] result = new float[dimension];

        for (int i = 0; i < dimension; i++) {
            result[i] = (float) values.get(i).asDouble();
        }

        return result;
    }
}