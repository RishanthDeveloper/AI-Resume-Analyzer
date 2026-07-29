package com.airesumeanalyzer.backend.service;

public interface GeminiClient {
    String generateContent(String prompt, String apiKey);
}
