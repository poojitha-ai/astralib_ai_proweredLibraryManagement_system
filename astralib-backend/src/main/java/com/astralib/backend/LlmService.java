package com.astralib.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class LlmService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    @Value("${llm.provider:none}")
    private String provider;

    @Value("${llm.chat-url:https://generativelanguage.googleapis.com/v1beta/models}")
    private String chatUrl;

    @Value("${llm.model:gemini-3.6-flash}")
    private String modelName;

    @Value("${llm.api-key:}")
    private String apiKey;

    /**
     * Generate a grounded answer using only AstraLib retrieved context.
     */
    public String generateGroundedAnswer(
            String question,
            String context) {

        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException(
                    "Question cannot be empty."
            );
        }

        if (context == null || context.isBlank()) {
            throw new IllegalArgumentException(
                    "AstraLib context cannot be empty."
            );
        }

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "Gemini API key is not configured. Set LLM_API_KEY."
            );
        }

        if (!"gemini".equalsIgnoreCase(provider)) {
            throw new IllegalStateException(
                    "Unsupported LLM provider: " + provider
                            + ". Set LLM_PROVIDER=gemini."
            );
        }

        return generateWithGemini(question, context);
    }

    /**
     * Send the grounded RAG prompt to Gemini.
     */
    private String generateWithGemini(
            String question,
            String context) {

        String prompt = buildGroundedPrompt(
                question,
                context
        );

        Map<String, Object> requestBody = Map.of(
                "contents",
                List.of(
                        Map.of(
                                "role", "user",
                                "parts", List.of(
                                        Map.of(
                                                "text",
                                                prompt
                                        )
                                )
                        )
                )
        );

        String endpoint =
                chatUrl
                        + "/"
                        + modelName
                        + ":generateContent";

        try {

            String jsonBody =
                    objectMapper.writeValueAsString(
                            requestBody
                    );

            HttpRequest request =
                    HttpRequest.newBuilder()
                            .uri(URI.create(endpoint))
                            .timeout(Duration.ofSeconds(60))
                            .header(
                                    "Content-Type",
                                    "application/json"
                            )
                            .header(
                                    "x-goog-api-key",
                                    apiKey
                            )
                            .POST(
                                    HttpRequest.BodyPublishers
                                            .ofString(jsonBody)
                            )
                            .build();

            HttpResponse<String> response =
                    httpClient.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

            if (
                    response.statusCode() < 200 ||
                    response.statusCode() >= 300
            ) {

                throw new IllegalStateException(
                        "Gemini API returned HTTP "
                                + response.statusCode()
                                + ": "
                                + extractGeminiError(
                                        response.body()
                                )
                );
            }

            JsonNode root =
                    objectMapper.readTree(
                            response.body()
                    );

            JsonNode candidates =
                    root.path("candidates");

            if (
                    !candidates.isArray() ||
                    candidates.isEmpty()
            ) {

                throw new IllegalStateException(
                        "Gemini returned no answer candidates."
                );
            }

            JsonNode parts =
                    candidates
                            .path(0)
                            .path("content")
                            .path("parts");

            if (
                    !parts.isArray() ||
                    parts.isEmpty()
            ) {

                throw new IllegalStateException(
                        "Gemini returned an empty response."
                );
            }

            StringBuilder answer =
                    new StringBuilder();

            for (JsonNode part : parts) {

                JsonNode text =
                        part.path("text");

                if (
                        text.isTextual() &&
                        !text.asText().isBlank()
                ) {

                    if (answer.length() > 0) {
                        answer.append("\n");
                    }

                    answer.append(
                            text.asText()
                    );
                }
            }

            if (answer.isEmpty()) {
                throw new IllegalStateException(
                        "Gemini returned no textual answer."
                );
            }

            return answer
                    .toString()
                    .trim();

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "Gemini request was interrupted.",
                    e
            );

        } catch (IllegalStateException e) {

            throw e;

        } catch (Exception e) {

            throw new IllegalStateException(
                    "Gemini request failed: "
                            + e.getMessage(),
                    e
            );
        }
    }

    /**
     * Grounding prompt.
     *
     * Gemini must answer only from AstraLib retrieval results.
     * Citations themselves are generated by RagService, not Gemini.
     */
    private String buildGroundedPrompt(
            String question,
            String context) {

        return """
                You are the AstraLib Institutional Research Assistant.

                Your task is to answer the user's research question using
                ONLY the ASTRA LIB CONTEXT provided below.

                IMPORTANT RULES:

                1. Use only information contained in the supplied context.

                2. Do not use outside knowledge to fill missing information.

                3. Do not invent books, authors, sections, quotations,
                   facts, citations, or document identifiers.

                4. If the supplied context does not contain enough
                   information to answer the question, clearly say:

                   "The available AstraLib sources do not contain
                   sufficient information to answer this question."

                5. Give a clear academic answer in simple,
                   understandable language.

                6. Do not create your own citation numbers.
                   AstraLib will attach verified citations separately.

                USER QUESTION:

                %s

                ASTRA LIB CONTEXT:

                %s

                GROUNDED ANSWER:
                """.formatted(
                question,
                context
        );
    }

    /**
     * Extract a useful error message from Gemini.
     */
    private String extractGeminiError(
            String responseBody) {

        try {

            JsonNode root =
                    objectMapper.readTree(
                            responseBody
                    );

            JsonNode message =
                    root
                            .path("error")
                            .path("message");

            if (message.isTextual()) {
                return message.asText();
            }

        } catch (Exception ignored) {
        }

        if (
                responseBody == null ||
                responseBody.isBlank()
        ) {
            return "Unknown Gemini API error.";
        }

        return responseBody;
    }
}