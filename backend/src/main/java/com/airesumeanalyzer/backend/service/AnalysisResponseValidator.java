package com.airesumeanalyzer.backend.service;

import com.airesumeanalyzer.backend.dto.AnalysisResponseDto;
import com.airesumeanalyzer.backend.exception.LlmUpstreamException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Component that parses and validates raw LLM JSON output against the required DTO structure.
 */
@Component
public class AnalysisResponseValidator {

    private final ObjectMapper objectMapper;

    public AnalysisResponseValidator() {
        this.objectMapper = new ObjectMapper();
    }

    public AnalysisResponseValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AnalysisResponseDto parseAndValidate(String jsonString) {
        if (jsonString == null || jsonString.isBlank()) {
            throw new LlmUpstreamException("Received empty JSON response from LLM.");
        }

        try {
            AnalysisResponseDto dto = objectMapper.readValue(jsonString, AnalysisResponseDto.class);

            if (dto.atsScore() == null) {
                throw new LlmUpstreamException("LLM response missing required 'atsScore' section.");
            }
            if (dto.skillGap() == null) {
                throw new LlmUpstreamException("LLM response missing required 'skillGap' section.");
            }
            if (dto.suggestions() == null) {
                throw new LlmUpstreamException("LLM response missing required 'suggestions' section.");
            }
            if (dto.jobMatching() == null) {
                throw new LlmUpstreamException("LLM response missing required 'jobMatching' section.");
            }
            if (dto.interviewQuestions() == null || dto.interviewQuestions().isEmpty()) {
                throw new LlmUpstreamException("LLM response missing or empty 'interviewQuestions' array.");
            }

            return dto;
        } catch (LlmUpstreamException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmUpstreamException("Model response could not be parsed into required AnalysisResponseDto schema: " + e.getMessage(), e);
        }
    }
}
