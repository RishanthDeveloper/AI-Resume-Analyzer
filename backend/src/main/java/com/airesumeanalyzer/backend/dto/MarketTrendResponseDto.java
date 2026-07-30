package com.airesumeanalyzer.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MarketTrendResponseDto(
        String targetRole,
        int totalPostingsAnalyzed,
        List<SkillDemandDto> trendingSkills,
        List<String> topMissingHighDemandSkills,
        List<SamplePostingDto> samplePostings
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SkillDemandDto(
            String skill,
            int count,
            double percentage,
            boolean presentInResume
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SamplePostingDto(
            String title,
            String companyName,
            String url,
            String publicationDate
    ) {}
}
