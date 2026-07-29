package com.airesumeanalyzer.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AnalysisResponseDto(
        AtsScoreDto atsScore,
        SkillGapDto skillGap,
        SuggestionsDto suggestions,
        JobMatchingDto jobMatching,
        List<InterviewQuestionDto> interviewQuestions
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AtsScoreDto(
            int score,
            AtsBreakdownDto breakdown,
            String summary
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AtsBreakdownDto(
            int formatting,
            int keywordMatch,
            int sectionCompleteness
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SkillGapDto(
            List<String> missingSkills,
            List<String> matchingSkills,
            String summary
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SuggestionsDto(
            List<LineRewriteDto> lineLevelRewrites,
            List<String> generalAdvice
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LineRewriteDto(
            String original,
            String suggested,
            String reason
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JobMatchingDto(
            int matchPercentage,
            String reasoning,
            List<String> keyStrengths,
            List<String> gaps
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InterviewQuestionDto(
            String question,
            String category,
            String keyPointsToCover
    ) {}
}
