package com.airesumeanalyzer.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Core service layer for the AI Resume Analyzer backend.
 * <p>
 * Responsibilities:
 * <ol>
 *   <li>Extract plain text from an uploaded resume PDF using Apache PDFBox.</li>
 *   <li>Build a structured prompt and call the Gemini {@code generateContent}
 *       REST endpoint directly over HTTP (no vendor SDK dependency).</li>
 *   <li>Return the model's markdown-formatted analysis report as plain text,
 *       ready for client-side markdown rendering.</li>
 * </ol>
 */
@Service
public class AnalysisService {

    private static final String GEMINI_ENDPOINT_TEMPLATE =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${gemini.model:gemini-2.5-flash}")
    private String geminiModel;

    @Value("${gemini.api.key:}")
    private String fallbackApiKey;

    /**
     * Extracts raw text content from an uploaded PDF using PDFBox's
     * {@code PDDocument.load} + {@code PDFTextStripper}.
     *
     * @throws IllegalArgumentException if the file is missing/empty or has no extractable text
     * @throws IOException              if the PDF cannot be parsed (corrupted/malformed file)
     */
    public String extractResumeText(MultipartFile resumeFile) throws IOException {
        if (resumeFile == null || resumeFile.isEmpty()) {
            throw new IllegalArgumentException("Uploaded resume file is empty.");
        }

        try (PDDocument document = Loader.loadPDF(resumeFile.getBytes())) {
            if (document.isEncrypted()) {
                throw new IllegalArgumentException("Uploaded PDF is password-protected and cannot be parsed.");
            }

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);

            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException("No extractable text was found in the uploaded PDF.");
            }
            return text.trim();
        } catch (IOException e) {
            throw new IOException("Failed to parse the uploaded PDF — the file may be corrupted: " + e.getMessage(), e);
        }
    }

    /**
     * Sends the extracted resume text and job description to Gemini and
     * returns a markdown-formatted analysis report.
     *
     * @param resumeText      plain text extracted from the resume PDF
     * @param jobDescription  the target job description
     * @param requestApiKey   optional per-request API key supplied by the client;
     *                        falls back to {@code gemini.api.key} if blank
     */
    public String analyzeResume(String resumeText, String jobDescription, String requestApiKey) {
        String apiKey = resolveApiKey(requestApiKey);

        if (jobDescription == null || jobDescription.isBlank()) {
            throw new IllegalArgumentException("Job description must not be empty.");
        }

        String prompt = buildPrompt(resumeText, jobDescription);
        String requestBody = buildGeminiRequestBody(prompt);
        String endpoint = String.format(GEMINI_ENDPOINT_TEMPLATE, geminiModel, apiKey);

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 401 || response.statusCode() == 403) {
                throw new IllegalArgumentException("Gemini API rejected the provided API key. Please verify it and try again.");
            }
            if (response.statusCode() >= 400) {
                throw new IllegalStateException(
                        "Gemini API returned an error (HTTP " + response.statusCode() + "): " + response.body());
            }

            return extractMarkdownFromResponse(response.body());
        } catch (java.net.http.HttpTimeoutException e) {
            throw new IllegalStateException("Timed out while waiting for the Gemini API to respond.", e);
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Failed to reach the Gemini API: " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------

    private String resolveApiKey(String requestApiKey) {
        String key = (requestApiKey != null && !requestApiKey.isBlank()) ? requestApiKey : fallbackApiKey;
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException(
                    "No Gemini API key was provided. Supply one via the request or configure gemini.api.key.");
        }
        return key;
    }

    private String buildPrompt(String resumeText, String jobDescription) {
        return """
                You are an expert ATS (Applicant Tracking System) auditor and professional resume
                reviewer. Compare the RESUME below against the JOB DESCRIPTION and produce a
                well-structured markdown report with the following sections, using headings,
                bullet points, and bold text where appropriate:

                ## Match Score
                State an overall ATS match percentage (0-100) with a one-line justification.

                ## Matched Keywords
                A bullet list of skills/technologies/qualifications present in both the resume
                and the job description.

                ## Missing Keywords
                A bullet list of important skills/technologies/qualifications mentioned in the
                job description but absent from the resume.

                ## Actionable Improvements
                3-5 specific, concrete suggestions to improve the resume's fit for this role.

                Respond with markdown only — no surrounding commentary, no code fences.

                RESUME:
                %s

                JOB DESCRIPTION:
                %s
                """.formatted(resumeText, jobDescription);
    }

    private String buildGeminiRequestBody(String prompt) {
        try {
            Map<String, Object> body = Map.of(
                    "contents", List.of(
                            Map.of("parts", List.of(Map.of("text", prompt)))
                    ),
                    "generationConfig", Map.of(
                            "temperature", 0.3
                    )
            );
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize the Gemini request payload.", e);
        }
    }

    private String extractMarkdownFromResponse(String rawBody) {
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            JsonNode candidates = root.path("candidates");

            if (!candidates.isArray() || candidates.isEmpty()) {
                throw new IllegalStateException("Gemini response contained no candidates.");
            }

            return candidates.get(0)
                    .path("content")
                    .path("parts").get(0)
                    .path("text")
                    .asText()
                    .trim();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse the Gemini API response: " + e.getMessage(), e);
        }
    }
}
