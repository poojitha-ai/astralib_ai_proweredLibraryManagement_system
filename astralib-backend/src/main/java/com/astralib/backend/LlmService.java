package com.astralib.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class LlmService {

    private static final Logger logger =
            LoggerFactory.getLogger(LlmService.class);

    private static final int MAX_RETRY_ATTEMPTS = 3;

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

        return sendAndReadAnswer(requestBody);
    }

    // =========================================================
    // CONVERSATIONAL CHAT
    // =========================================================

    /** One prior turn sent back to the model. */
    public record Turn(String role, String text) {

        public boolean isAssistant() {
            return "model".equals(role) || "assistant".equals(role);
        }
    }

    /**
     * @return true when a chat provider and key are configured. Callers use
     *         this to fall back to a deterministic answer instead of failing
     *         the whole conversation when the model is unavailable.
     */
    public boolean isConfigured() {
        return apiKey != null
                && !apiKey.isBlank()
                && "gemini".equalsIgnoreCase(provider);
    }

    /**
     * Conversational completion with system instructions and prior turns.
     *
     * The Research Assistant needs the model to see the conversation, not a
     * single question: "yes, that one" only means something after the answer
     * that offered it.
     */
    public String chat(
            String systemInstruction,
            List<Turn> turns) {

        if (!isConfigured()) {
            throw new IllegalStateException(
                    "No chat model is configured. Set LLM_PROVIDER=gemini and LLM_API_KEY."
            );
        }

        if (turns == null || turns.isEmpty()) {
            throw new IllegalArgumentException(
                    "A conversation needs at least one turn."
            );
        }

        List<Map<String, Object>> contents = new java.util.ArrayList<>();

        for (Turn turn : turns) {
            if (turn == null || turn.text() == null || turn.text().isBlank()) {
                continue;
            }

            contents.add(
                    Map.of(
                            "role",
                            turn.isAssistant() ? "model" : "user",
                            "parts",
                            List.of(
                                    Map.of("text", turn.text())
                            )
                    )
            );
        }

        if (contents.isEmpty()) {
            throw new IllegalArgumentException(
                    "A conversation needs at least one non-empty turn."
            );
        }

        Map<String, Object> requestBody = new java.util.LinkedHashMap<>();

        if (systemInstruction != null && !systemInstruction.isBlank()) {
            requestBody.put(
                    "systemInstruction",
                    Map.of(
                            "parts",
                            List.of(Map.of("text", systemInstruction))
                    )
            );
        }

        requestBody.put("contents", contents);
        requestBody.put(
                "generationConfig",
                Map.of(
                        "temperature", 0.7,
                        "maxOutputTokens", 900
                )
        );

        return sendAndReadAnswer(requestBody);
    }

    /**
     * Shared transport: POST a generateContent body and read the answer text.
     */
    private String sendAndReadAnswer(
            Map<String, Object> requestBody) {

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

            // Retry temporary Gemini API errors before parsing the answer.
            HttpResponse<String> response =
                    sendWithRetry(request);

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
     * Retry requests that fail because of temporary server errors
     * or rate limiting.
     *
     * Retryable status codes:
     * 429, 500, 502, 503 and 504.
     *
     * Other unsuccessful responses are returned immediately so that
     * the caller can report the actual API error.
     */
    private HttpResponse<String> sendWithRetry(
            HttpRequest request)
            throws IOException, InterruptedException {

        for (
                int attempt = 1;
                attempt <= MAX_RETRY_ATTEMPTS;
                attempt++
        ) {

            HttpResponse<String> response =
                    httpClient.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

            int status = response.statusCode();

            boolean successful =
                    status >= 200 && status < 300;

            boolean retryable =
                    status == 429 ||
                    status == 500 ||
                    status == 502 ||
                    status == 503 ||
                    status == 504;

            // Return successful responses and non-retryable errors.
            if (
                    successful ||
                    !retryable ||
                    attempt == MAX_RETRY_ATTEMPTS
            ) {
                return response;
            }

            long delayMillis = 1000L * attempt;

            logger.warn(
                    "Gemini returned HTTP {} on attempt {}/{}. "
                            + "Retrying in {} ms.",
                    status,
                    attempt,
                    MAX_RETRY_ATTEMPTS,
                    delayMillis
            );

            Thread.sleep(delayMillis);
        }

        // Defensive fallback; normally the loop returns or throws.
        throw new IllegalStateException(
                "Gemini retry loop ended unexpectedly."
        );
    }

    /**
     * Grounding prompt.
     *
     * Gemini must answer only from AstraLib retrieved context.
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
            // Use the response body below as a fallback.
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