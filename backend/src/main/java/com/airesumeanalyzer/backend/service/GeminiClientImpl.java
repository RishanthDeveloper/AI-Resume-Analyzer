package com.airesumeanalyzer.backend.service;

import com.airesumeanalyzer.backend.exception.LlmUpstreamException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class GeminiClientImpl implements GeminiClient {

    private static final String GEMINI_ENDPOINT_TEMPLATE =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${gemini.model:gemini-2.5-flash}")
    private String geminiModel;

    @Value("${gemini.api.key:}")
    private String fallbackApiKey;

    public GeminiClientImpl() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public GeminiClientImpl(HttpClient httpClient, ObjectMapper objectMapper, String geminiModel, String fallbackApiKey) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.geminiModel = geminiModel;
        this.fallbackApiKey = fallbackApiKey;
    }

    @Override
    public String generateContent(String prompt, String requestApiKey) {
        String apiKey = (requestApiKey != null && !requestApiKey.isBlank()) ? requestApiKey : fallbackApiKey;
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException(
                    "No Gemini API key was provided. Supply one via the request or configure gemini.api.key.");
        }

        String requestBody = buildGeminiRequestBody(prompt);
        String endpoint = String.format(GEMINI_ENDPOINT_TEMPLATE, geminiModel, apiKey);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(45))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 401 || response.statusCode() == 403) {
                throw new IllegalArgumentException("Gemini API rejected the provided API key. Please verify it and try again.");
            }
            if (response.statusCode() >= 400) {
                throw new LlmUpstreamException(
                        "Gemini API returned an error (HTTP " + response.statusCode() + "): " + response.body());
            }

            return extractJsonFromResponse(response.body());
        } catch (java.net.http.HttpTimeoutException e) {
            throw new LlmUpstreamException("Timed out while waiting for the Gemini API to respond.", e);
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmUpstreamException("Failed to reach the Gemini API: " + e.getMessage(), e);
        }
    }

    private String buildGeminiRequestBody(String prompt) {
        try {
            Map<String, Object> body = Map.of(
                    "contents", List.of(
                            Map.of("parts", List.of(Map.of("text", prompt)))
                    ),
                    "generationConfig", Map.of(
                            "temperature", 0.2,
                            "responseMimeType", "application/json"
                    )
            );
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new LlmUpstreamException("Failed to serialize the Gemini request payload.", e);
        }
    }

    private String extractJsonFromResponse(String rawBody) {
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            JsonNode candidates = root.path("candidates");

            if (!candidates.isArray() || candidates.isEmpty()) {
                throw new LlmUpstreamException("Gemini response contained no candidates.");
            }

            String text = candidates.get(0)
                    .path("content")
                    .path("parts").get(0)
                    .path("text")
                    .asText()
                    .trim();

            if (text.startsWith("```")) {
                text = text.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "").trim();
            }

            return text;
        } catch (Exception e) {
            throw new LlmUpstreamException("Failed to parse the Gemini API response wrapper: " + e.getMessage(), e);
        }
    }
}
