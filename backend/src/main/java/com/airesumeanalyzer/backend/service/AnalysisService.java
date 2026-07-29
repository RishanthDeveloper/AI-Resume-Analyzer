package com.airesumeanalyzer.backend.service;

import com.airesumeanalyzer.backend.dto.AnalysisResponseDto;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Core orchestrator service delegating to specialized components:
 * PdfTextExtractor, GeminiPromptBuilder, GeminiClient, and AnalysisResponseValidator.
 */
@Service
public class AnalysisService {

    private final PdfTextExtractor pdfTextExtractor;
    private final GeminiPromptBuilder geminiPromptBuilder;
    private final GeminiClient geminiClient;
    private final AnalysisResponseValidator analysisResponseValidator;

    public AnalysisService(
            PdfTextExtractor pdfTextExtractor,
            GeminiPromptBuilder geminiPromptBuilder,
            GeminiClient geminiClient,
            AnalysisResponseValidator analysisResponseValidator
    ) {
        this.pdfTextExtractor = pdfTextExtractor;
        this.geminiPromptBuilder = geminiPromptBuilder;
        this.geminiClient = geminiClient;
        this.analysisResponseValidator = analysisResponseValidator;
    }

    public String extractResumeText(MultipartFile resumeFile) {
        return pdfTextExtractor.extractResumeText(resumeFile);
    }

    public AnalysisResponseDto analyzeResume(String resumeText, String jobDescription, String requestApiKey) {
        if (jobDescription == null || jobDescription.isBlank()) {
            throw new IllegalArgumentException("Job description must not be empty.");
        }

        String prompt = geminiPromptBuilder.buildStructuredPrompt(resumeText, jobDescription);
        String rawJson = geminiClient.generateContent(prompt, requestApiKey);
        return analysisResponseValidator.parseAndValidate(rawJson);
    }
}
