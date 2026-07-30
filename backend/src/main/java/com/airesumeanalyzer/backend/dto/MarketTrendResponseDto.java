package com.airesumeanalyzer.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Response payload for the Live Job Market Skill Radar feature.
 * Unlike the single-JD comparison in {@link AnalysisResponseDto}, this reflects
 * REAL-TIME demand mined from currently open job postings for a target role.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MarketTrendResponseDto(
        String role,
        int jobsAnalyzed,
        String generatedAt,
        List<TrendingSkillDto> trendingSkills,
        List<String> topMissingHighDemandSkills,
        List<SamplePostingDto> samplePostings
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TrendingSkillDto(
            String skill,
            int demandCount,
            int demandPercentage,
            boolean inResume
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SamplePostingDto(
            String title,
            String company,
            String url,
            List<String> tags
    ) {}
}
