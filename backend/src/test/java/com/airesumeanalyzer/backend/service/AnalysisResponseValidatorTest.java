package com.airesumeanalyzer.backend.service;

import com.airesumeanalyzer.backend.dto.AnalysisResponseDto;
import com.airesumeanalyzer.backend.exception.LlmUpstreamException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AnalysisResponseValidatorTest {

    private AnalysisResponseValidator validator;

    @BeforeEach
    void setUp() {
        validator = new AnalysisResponseValidator();
    }

    @Test
    void parseAndValidate_ValidJson_ReturnsDto() {
        String validJson = """
                {
                  "atsScore": {
                    "score": 88,
                    "breakdown": { "formatting": 90, "keywordMatch": 85, "sectionCompleteness": 90 },
                    "summary": "Excellent match"
                  },
                  "skillGap": {
                    "missingSkills": ["Kubernetes"],
                    "matchingSkills": ["Java", "Spring Boot"],
                    "summary": "Minor skill gap"
                  },
                  "suggestions": {
                    "lineLevelRewrites": [
                      { "original": "Built app", "suggested": "Architected microservice", "reason": "Quantified" }
                    ],
                    "generalAdvice": ["Add metrics"]
                  },
                  "jobMatching": {
                    "matchPercentage": 88,
                    "reasoning": "High alignment",
                    "keyStrengths": ["Java"],
                    "gaps": ["Kubernetes"]
                  },
                  "interviewQuestions": [
                    { "question": "Explain Spring Boot DI", "category": "Core Java", "keyPointsToCover": "IoC container" }
                  ]
                }
                """;

        AnalysisResponseDto dto = validator.parseAndValidate(validJson);
        assertNotNull(dto);
        assertEquals(88, dto.atsScore().score());
        assertEquals(1, dto.interviewQuestions().size());
    }

    @Test
    void parseAndValidate_MissingAtsScore_ThrowsLlmUpstreamException() {
        String invalidJson = """
                {
                  "skillGap": { "missingSkills": [], "matchingSkills": [], "summary": "s" },
                  "suggestions": { "lineLevelRewrites": [], "generalAdvice": [] },
                  "jobMatching": { "matchPercentage": 80, "reasoning": "r", "keyStrengths": [], "gaps": [] },
                  "interviewQuestions": [{ "question": "q", "category": "c", "keyPointsToCover": "k" }]
                }
                """;

        assertThrows(LlmUpstreamException.class, () -> validator.parseAndValidate(invalidJson));
    }

    @Test
    void parseAndValidate_EmptyQuestions_ThrowsLlmUpstreamException() {
        String invalidJson = """
                {
                  "atsScore": { "score": 80, "breakdown": { "formatting": 80, "keywordMatch": 80, "sectionCompleteness": 80 }, "summary": "s" },
                  "skillGap": { "missingSkills": [], "matchingSkills": [], "summary": "s" },
                  "suggestions": { "lineLevelRewrites": [], "generalAdvice": [] },
                  "jobMatching": { "matchPercentage": 80, "reasoning": "r", "keyStrengths": [], "gaps": [] },
                  "interviewQuestions": []
                }
                """;

        assertThrows(LlmUpstreamException.class, () -> validator.parseAndValidate(invalidJson));
    }
}
