package com.airesumeanalyzer.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
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
 *   <li>Extract plain text from an uploaded resume PDF using Apache PDFBox 3.x.</li>
 *   <li>Build a structured JSON prompt and call the Gemini 2.5 Flash {@code generateContent}
 *       REST endpoint directly over HTTP (no vendor SDK dependency).</li>
 *   <li>Return a strict, structured JSON Map with all 5 core features:
 *       ATS Score, Skill Gap Analysis, Resume Suggestions, Job Matching, and Interview Questions.</li>
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
     * Extracts raw text content from an uploaded PDF using PDFBox 3.x
     * {@code Loader.loadPDF(byte[])} + {@code PDFTextStripper}.
     *
     * @throws IllegalArgumentException if the file is missing/empty or has no extractable text
     * @throws IOException              if the PDF cannot be parsed
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
     * returns a structured Map representing the 5 analysis sections.
     *
     * @param resumeText      plain text extracted from the resume PDF
     * @param jobDescription  the target job description
     * @param requestApiKey   optional per-request API key supplied by the client;
     *                        falls back to {@code gemini.api.key} if blank
     */
    public Map<String, Object> analyzeResume(String resumeText, String jobDescription, String requestApiKey) {
        String apiKey = resolveApiKey(requestApiKey);

        if (jobDescription == null || jobDescription.isBlank()) {
            throw new IllegalArgumentException("Job description must not be empty.");
        }

        String prompt = buildStructuredPrompt(resumeText, jobDescription);
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
                throw new IllegalStateException(
                        "Gemini API returned an error (HTTP " + response.statusCode() + "): " + response.body());
            }

            String rawJsonContent = extractJsonFromResponse(response.body());
            return parseAndValidateAnalysisJson(rawJsonContent);
        } catch (java.net.http.HttpTimeoutException e) {
            throw new IllegalStateException("Timed out while waiting for the Gemini API to respond.", e);
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Failed to reach the Gemini API: " + e.getMessage(), e);
        }
    }

    private String resolveApiKey(String requestApiKey) {
        String key = (requestApiKey != null && !requestApiKey.isBlank()) ? requestApiKey : fallbackApiKey;
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException(
                    "No Gemini API key was provided. Supply one via the request or configure gemini.api.key.");
        }
        return key;
    }

    private String buildStructuredPrompt(String resumeText, String jobDescription) {
        return """
                You are an expert ATS (Applicant Tracking System) auditor, technical recruiter, and interview strategist for placement interviews.
                Compare the provided RESUME against the target JOB DESCRIPTION.

                Respond ONLY with a valid, raw JSON object (strictly no markdown formatting, no code fences like ```json, no extra preamble).

                The JSON object MUST strictly adhere to this structure:

                {
                  "atsScore": {
                    "score": 85,
                    "breakdown": {
                      "formatting": 90,
                      "keywordMatch": 80,
                      "sectionCompleteness": 85
                    },
                    "summary": "Short evaluation summary of overall ATS compatibility."
                  },
                  "skillGap": {
                    "missingSkills": ["Docker", "Kubernetes", "Redis"],
                    "matchingSkills": ["Java 17", "Spring Boot", "REST APIs"],
                    "summary": "Detailed breakdown of technical and domain skills gap."
                  },
                  "suggestions": {
                    "lineLevelRewrites": [
                      {
                        "original": "Worked on backend service with Spring",
                        "suggested": "Architected microservices using Spring Boot 3.2, reducing API latency by 35%",
                        "reason": "Quantify achievement and highlight specific framework versions"
                      }
                    ],
                    "generalAdvice": [
                      "Include metric-driven bullet points for all recent experience.",
                      "Add a Dedicated Technical Skills matrix at top."
                    ]
                  },
                  "jobMatching": {
                    "matchPercentage": 82,
                    "reasoning": "Strong alignment on core Java backend skills with minor gap in cloud infrastructure.",
                    "keyStrengths": ["Core Java Expertise", "RESTful Architecture", "Database Design"],
                    "gaps": ["Cloud Deployment Experience", "Containerization"]
                  },
                  "interviewQuestions": [
                    {
                      "question": "Can you explain how you designed your Spring Boot services to handle high concurrency?",
                      "category": "Technical / Backend Architecture",
                      "keyPointsToCover": "Discuss connection pooling, stateless REST APIs, caching, and async processing."
                    },
                    {
                      "question": "Tell me about a time you optimized a slow database query or API endpoint.",
                      "category": "Problem Solving / Performance Tuning",
                      "keyPointsToCover": "Explain indexing, query profiling, caching strategies, and measured performance gains."
                    }
                  ]
                }

                Provide 5 to 8 realistic, relevant interview questions tailored to placement interviews for this role in the interviewQuestions array.

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
                            "temperature", 0.2,
                            "responseMimeType", "application/json"
                    )
            );
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize the Gemini request payload.", e);
        }
    }

    private String extractJsonFromResponse(String rawBody) {
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            JsonNode candidates = root.path("candidates");

            if (!candidates.isArray() || candidates.isEmpty()) {
                throw new IllegalStateException("Gemini response contained no candidates.");
            }

            String text = candidates.get(0)
                    .path("content")
                    .path("parts").get(0)
                    .path("text")
                    .asText()
                    .trim();

            // Clean markdown code blocks if the model wrapped it despite system prompt
            if (text.startsWith("```")) {
                text = text.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "").trim();
            }

            return text;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse the Gemini API response wrapper: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> parseAndValidateAnalysisJson(String jsonString) {
        try {
            return objectMapper.readValue(jsonString, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Model response could not be parsed as valid JSON: " + e.getMessage(), e);
        }
    }
}
