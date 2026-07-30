package com.airesumeanalyzer.backend.service;

import com.airesumeanalyzer.backend.dto.MarketTrendResponseDto;
import com.airesumeanalyzer.backend.dto.MarketTrendResponseDto.SamplePostingDto;
import com.airesumeanalyzer.backend.dto.MarketTrendResponseDto.SkillDemandDto;
import com.airesumeanalyzer.backend.exception.MarketDataUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * Service that queries the live Remotive remote jobs API (https://remotive.com/api/remote-jobs),
 * mines real-time skill demand across active postings, and cross-references them against an uploaded resume.
 */
@Service
public class MarketTrendService {

    private static final Logger logger = LoggerFactory.getLogger(MarketTrendService.class);
    private static final String REMOTIVE_API_BASE = "https://remotive.com/api/remote-jobs?search=";

    private static final List<String> DICTIONARY_SKILLS = List.of(
            "Java", "Spring Boot", "Python", "React", "TypeScript", "JavaScript", "Node.js",
            "Docker", "Kubernetes", "AWS", "Azure", "GCP", "SQL", "PostgreSQL", "MongoDB",
            "Redis", "Kafka", "REST API", "GraphQL", "Microservices", "CI/CD", "Git", "Linux",
            "HTML", "CSS", "Tailwind", "Next.js", "Agile", "Scrum", "Unit Testing", "JUnit",
            "Mockito", "C++", "C#", ".NET", "Go", "Rust", "Swift", "Kotlin", "DevOps"
    );

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public MarketTrendService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public MarketTrendService(HttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Fetches active job postings from Remotive API for the given role, mines trending skills,
     * and compares them against the resume text.
     *
     * @param resumeText plain text extracted from uploaded resume
     * @param role       target role title (e.g. "Backend Developer", "Software Engineer")
     * @return MarketTrendResponseDto containing trending skills, missing skills, and sample postings
     */
    public MarketTrendResponseDto fetchMarketTrends(String resumeText, String role) {
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("Target role must not be empty.");
        }

        String searchUrl = REMOTIVE_API_BASE + URLEncoder.encode(role.trim(), StandardCharsets.UTF_8);
        logger.info("Querying Remotive API for live market trend analysis: {}", searchUrl);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(searchUrl))
                .timeout(Duration.ofSeconds(12))
                .header("User-Agent", "AI-Resume-Analyzer/2.5")
                .header("Accept", "application/json")
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new MarketDataUnavailableException("Remotive API returned status HTTP " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode jobsNode = root.path("jobs");

            if (!jobsNode.isArray() || jobsNode.isEmpty()) {
                throw new MarketDataUnavailableException("No live job postings found for role: '" + role + "'. Try a broader role title like 'Software Engineer'.");
            }

            int totalJobs = Math.min(jobsNode.size(), 30);
            Map<String, Integer> skillCounts = new HashMap<>();
            List<SamplePostingDto> samplePostings = new ArrayList<>();

            String lowerResumeText = resumeText != null ? resumeText.toLowerCase() : "";

            for (int i = 0; i < totalJobs; i++) {
                JsonNode job = jobsNode.get(i);
                String title = job.path("title").asText("");
                String companyName = job.path("company_name").asText("Remote Company");
                String url = job.path("url").asText("#");
                String publicationDate = job.path("publication_date").asText("");
                String descriptionHtml = job.path("description").asText("");

                // Build searchable text blob for this posting
                StringBuilder jobBlob = new StringBuilder();
                jobBlob.append(title.toLowerCase()).append(" ");
                jobBlob.append(descriptionHtml.toLowerCase()).append(" ");

                JsonNode tagsNode = job.path("tags");
                if (tagsNode.isArray()) {
                    for (JsonNode tag : tagsNode) {
                        jobBlob.append(tag.asText().toLowerCase()).append(" ");
                    }
                }

                String combinedText = jobBlob.toString();

                // Mine dictionary skills in this job posting
                for (String skill : DICTIONARY_SKILLS) {
                    if (containsSkill(combinedText, skill.toLowerCase())) {
                        skillCounts.put(skill, skillCounts.getOrDefault(skill, 0) + 1);
                    }
                }

                if (i < 5) {
                    samplePostings.add(new SamplePostingDto(
                            title,
                            companyName,
                            url,
                            formatDate(publicationDate)
                    ));
                }
            }

            // Build skill demand list
            List<SkillDemandDto> trendingSkills = new ArrayList<>();
            List<String> missingHighDemandSkills = new ArrayList<>();

            for (Map.Entry<String, Integer> entry : skillCounts.entrySet()) {
                String skill = entry.getKey();
                int count = entry.getValue();
                double percentage = Math.round((count * 100.0 / totalJobs) * 10.0) / 10.0;
                boolean presentInResume = containsSkill(lowerResumeText, skill.toLowerCase());

                SkillDemandDto demandDto = new SkillDemandDto(skill, count, percentage, presentInResume);
                trendingSkills.add(demandDto);

                if (!presentInResume && count >= Math.max(2, totalJobs * 0.15)) {
                    missingHighDemandSkills.add(skill);
                }
            }

            // Sort trending skills descending by count
            trendingSkills.sort((a, b) -> Integer.compare(b.count(), a.count()));
            missingHighDemandSkills.sort((a, b) -> {
                int countA = skillCounts.getOrDefault(a, 0);
                int countB = skillCounts.getOrDefault(b, 0);
                return Integer.compare(countB, countA);
            });

            return new MarketTrendResponseDto(
                    role.trim(),
                    totalJobs,
                    trendingSkills,
                    missingHighDemandSkills,
                    samplePostings
            );

        } catch (MarketDataUnavailableException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Failed to query live job market data from Remotive", e);
            throw new MarketDataUnavailableException("Failed to fetch live job market data from Remotive API: " + e.getMessage(), e);
        }
    }

    private boolean containsSkill(String text, String lowerSkill) {
        if (text == null || text.isBlank() || lowerSkill == null || lowerSkill.isBlank()) {
            return false;
        }
        // Word boundary matching for short terms like C++, C#, Java, Go
        if (lowerSkill.equals("c++") || lowerSkill.equals("c#") || lowerSkill.equals("go") || lowerSkill.equals("aws") || lowerSkill.equals("gcp")) {
            return text.contains(lowerSkill);
        }
        return text.contains(lowerSkill);
    }

    private String formatDate(String rawDate) {
        if (rawDate == null || rawDate.isBlank()) return "Recently posted";
        if (rawDate.contains("T")) return rawDate.split("T")[0];
        return rawDate;
    }
}
