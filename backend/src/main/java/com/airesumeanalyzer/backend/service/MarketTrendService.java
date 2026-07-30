package com.airesumeanalyzer.backend.service;

import com.airesumeanalyzer.backend.dto.MarketTrendResponseDto;
import com.airesumeanalyzer.backend.dto.MarketTrendResponseDto.SamplePostingDto;
import com.airesumeanalyzer.backend.dto.MarketTrendResponseDto.TrendingSkillDto;
import com.airesumeanalyzer.backend.exception.MarketDataUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Live Job Market Skill Radar.
 * <p>
 * Real-world problem this solves: candidates typically optimize their resume against a single,
 * often stale, job description. The actual market shifts week to week. This service pulls
 * CURRENTLY OPEN postings for a target role from the Remotive public jobs API (no key required),
 * mines the skills genuinely in demand right now across those live postings, and cross-references
 * them against the candidate's resume text — surfacing which in-demand skills they already have
 * and which ones they are missing, ranked by live market frequency.
 */
@Service
public class MarketTrendService {

    private static final Logger logger = LoggerFactory.getLogger(MarketTrendService.class);
    private static final String REMOTIVE_API = "https://remotive.com/api/remote-jobs";
    private static final int MAX_JOBS = 30;
    private static final int TOP_SKILLS = 15;
    private static final int TOP_MISSING = 6;
    private static final int SAMPLE_POSTINGS = 5;

    // Curated fallback dictionary used to mine skills out of free-text descriptions
    // for postings that don't carry rich structured tags.
    private static final List<String> SKILL_DICTIONARY = List.of(
            "Java", "Python", "JavaScript", "TypeScript", "React", "Angular", "Vue",
            "Node.js", "Spring Boot", "Spring", "Django", "Flask", "FastAPI",
            "AWS", "Azure", "GCP", "Docker", "Kubernetes", "Terraform", "Jenkins",
            "CI/CD", "Git", "GitHub Actions", "Microservices", "REST API", "GraphQL",
            "SQL", "PostgreSQL", "MySQL", "MongoDB", "Redis", "Kafka", "RabbitMQ",
            "Machine Learning", "Deep Learning", "TensorFlow", "PyTorch", "NLP",
            "Data Analysis", "Pandas", "NumPy", "Spark", "Hadoop", "ETL",
            "HTML", "CSS", "Tailwind", "Next.js", "Express", "Go", "Rust", "C++",
            "C#", ".NET", "PHP", "Ruby on Rails", "Swift", "Kotlin", "Flutter",
            "Android", "iOS", "Selenium", "JUnit", "Agile", "Scrum", "DevOps",
            "Linux", "Bash", "Shell Scripting", "System Design", "OOP",
            "Blockchain", "Solidity", "Web3", "Cybersecurity", "OAuth", "JWT",
            "Figma", "Product Management", "Salesforce", "Tableau", "Power BI"
    );

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MarketTrendService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public MarketTrendService(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public MarketTrendResponseDto analyzeMarketTrends(String role, String resumeText) {
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("Target role must not be empty.");
        }

        List<JsonNode> jobs = fetchLiveJobs(role.trim());
        if (jobs.isEmpty()) {
            throw new MarketDataUnavailableException(
                    "No live postings found for \"" + role + "\" right now. Try a broader role title (e.g. \"Backend Developer\").");
        }

        Map<String, Integer> skillCounts = new LinkedHashMap<>();
        for (JsonNode job : jobs) {
            for (String skill : extractSkills(job)) {
                skillCounts.merge(skill, 1, Integer::sum);
            }
        }

        String normalizedResume = resumeText == null ? "" : resumeText.toLowerCase(Locale.ROOT);

        List<TrendingSkillDto> trending = skillCounts.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(TOP_SKILLS)
                .map(e -> new TrendingSkillDto(
                        e.getKey(),
                        e.getValue(),
                        (int) Math.round((e.getValue() * 100.0) / jobs.size()),
                        containsSkill(normalizedResume, e.getKey())
                ))
                .toList();

        List<String> missingHighDemand = trending.stream()
                .filter(t -> !t.inResume())
                .map(TrendingSkillDto::skill)
                .limit(TOP_MISSING)
                .toList();

        List<SamplePostingDto> samples = jobs.stream()
                .limit(SAMPLE_POSTINGS)
                .map(this::toSamplePosting)
                .toList();

        return new MarketTrendResponseDto(
                role.trim(),
                jobs.size(),
                Instant.now().toString(),
                trending,
                missingHighDemand,
                samples
        );
    }

    private List<JsonNode> fetchLiveJobs(String role) {
        try {
            String encodedRole = URLEncoder.encode(role, StandardCharsets.UTF_8);
            URI uri = URI.create(REMOTIVE_API + "?search=" + encodedRole + "&limit=" + MAX_JOBS);
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(6))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new MarketDataUnavailableException(
                        "Live job market service returned status " + response.statusCode() + ". Please try again shortly.");
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode jobsNode = root.path("jobs");
            List<JsonNode> jobs = new ArrayList<>();
            if (jobsNode.isArray()) {
                jobsNode.forEach(jobs::add);
            }
            return jobs;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            logger.warn("Failed to fetch live job postings for role '{}': {}", role, e.getMessage());
            throw new MarketDataUnavailableException(
                    "Unable to reach the live job market service right now. Please try again in a moment.", e);
        }
    }

    private Set<String> extractSkills(JsonNode job) {
        Set<String> found = new LinkedHashSet<>();

        JsonNode tagsNode = job.path("tags");
        if (tagsNode.isArray()) {
            for (JsonNode tag : tagsNode) {
                String cleaned = cleanTag(tag.asText(""));
                if (!cleaned.isBlank()) {
                    found.add(cleaned);
                }
            }
        }

        String description = job.path("description").asText("");
        String title = job.path("title").asText("");
        String haystack = (title + " " + stripHtml(description)).toLowerCase(Locale.ROOT);

        for (String skill : SKILL_DICTIONARY) {
            if (containsSkill(haystack, skill)) {
                found.add(skill);
            }
        }

        return found;
    }

    private String cleanTag(String raw) {
        String trimmed = raw.trim();
        if (trimmed.isEmpty() || trimmed.length() > 30) {
            return "";
        }
        if (trimmed.matches("[a-zA-Z0-9+.#-]+") && trimmed.equals(trimmed.toLowerCase(Locale.ROOT))) {
            return Character.toUpperCase(trimmed.charAt(0)) + trimmed.substring(1);
        }
        return trimmed;
    }

    private boolean containsSkill(String haystackLower, String skill) {
        String escaped = Pattern.quote(skill.toLowerCase(Locale.ROOT));
        return Pattern.compile("(?<![a-z0-9])" + escaped + "(?![a-z0-9])").matcher(haystackLower).find();
    }

    private String stripHtml(String html) {
        return html.replaceAll("<[^>]*>", " ");
    }

    private SamplePostingDto toSamplePosting(JsonNode job) {
        List<String> tags = new ArrayList<>();
        JsonNode tagsNode = job.path("tags");
        if (tagsNode.isArray()) {
            int count = 0;
            for (JsonNode tag : tagsNode) {
                if (count++ >= 4) break;
                tags.add(tag.asText(""));
            }
        }
        return new SamplePostingDto(
                job.path("title").asText(""),
                job.path("company_name").asText(""),
                job.path("url").asText(""),
                tags
        );
    }
}
