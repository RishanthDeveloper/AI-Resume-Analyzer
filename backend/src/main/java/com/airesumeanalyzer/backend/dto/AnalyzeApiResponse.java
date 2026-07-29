package com.airesumeanalyzer.backend.dto;

public record AnalyzeApiResponse(
        AnalysisResponseDto analysis,
        boolean savedToHistory,
        String timestamp
) {}
