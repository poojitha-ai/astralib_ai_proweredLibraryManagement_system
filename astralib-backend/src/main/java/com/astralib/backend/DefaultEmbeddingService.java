package com.astralib.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DefaultEmbeddingService implements EmbeddingService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${llm.embedding-provider:none}")
    private String provider;

    @Value("${llm.embedding-model:gemini-embedding-001}")
    private String modelName;

    @Value("${llm.embedding-dimension:384}")
    private int dimension;

    @Value("${llm.api-key:}")
    private String apiKey;

    @Override
    public int getDimension() {
        return dimension;
    }

    @Override
    public float[] generateEmbedding(String text) {

        if (text == null || text.isBlank()) {
            return new float[dimension];
        }

        if (!"gemini".equalsIgnoreCase(provider)) {
            throw new IllegalStateException(
                    "Gemini embedding provider is not configured. " +
                    "Set LLM_EMBEDDING_PROVIDER=gemini."
            );
        }

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "Gemini API key is missing. Set LLM_API_KEY."
            );
        }

        try {

            // Gemini embedding endpoint
            String endpoint =
                    "https://generativelanguage.googleapis.com/v1beta/models/"
                            + modelName
                            + ":embedContent";

            // Request body
            Map<String, Object> textPart =
                    new LinkedHashMap<>();

            textPart.put("text", text);

            Map<String, Object> content =
                    new LinkedHashMap<>();

            content.put(
                    "parts",
                    List.of(textPart)
            );

            Map<String, Object> body =
                    new LinkedHashMap<>();

            body.put(
                    "model",
                    "models/" + modelName
            );

            body.put(
                    "content",
                    content
            );

            body.put(
                    "outputDimensionality",
                    dimension
            );

            String jsonBody =
                    objectMapper.writeValueAsString(body);

            HttpRequest request =
                    HttpRequest
                            .newBuilder(
                                    URI.create(endpoint)
                            )
                            .header(
                                    "Content-Type",
                                    "application/json"
                            )
                            .header(
                                    "x-goog-api-key",
                                    apiKey
                            )
                            .POST(
                                    HttpRequest
                                            .BodyPublishers
                                            .ofString(jsonBody)
                            )
                            .build();

            HttpResponse<String> response =
                    httpClient.send(
                            request,
                            HttpResponse
                                    .BodyHandlers
                                    .ofString()
                    );

            if (
                    response.statusCode() < 200 ||
                    response.statusCode() >= 300
            ) {
                throw new IllegalStateException(
                        "Gemini embedding API returned HTTP "
                                + response.statusCode()
                                + ": "
                                + response.body()
                );
            }

            JsonNode root =
                    objectMapper.readTree(
                            response.body()
                    );

            JsonNode values =
                    root
                            .path("embedding")
                            .path("values");

            if (!values.isArray()) {
                throw new IllegalStateException(
                        "Gemini response did not contain embedding.values"
                );
            }

            if (values.size() != dimension) {
                throw new IllegalStateException(
                        "Unexpected Gemini embedding dimension. Expected "
                                + dimension
                                + " but received "
                                + values.size()
                );
            }

            float[] result =
                    new float[dimension];

            for (int i = 0; i < dimension; i++) {
                result[i] =
                        (float) values
                                .get(i)
                                .asDouble();
            }

            return result;

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "Gemini embedding request interrupted.",
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
}