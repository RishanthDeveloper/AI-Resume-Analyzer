package com.airesumeanalyzer.backend.controller;

import com.airesumeanalyzer.backend.dto.AnalysisResponseDto;
import com.airesumeanalyzer.backend.dto.AnalyzeApiResponse;
import com.airesumeanalyzer.backend.dto.MarketTrendResponseDto;
import com.airesumeanalyzer.backend.exception.RateLimitExceededException;
import com.airesumeanalyzer.backend.repository.HistoryRepository;
import com.airesumeanalyzer.backend.service.AnalysisService;
import com.airesumeanalyzer.backend.service.MarketTrendService;
import com.airesumeanalyzer.backend.service.RateLimiterService;
import com.airesumeanalyzer.backend.service.SupabaseAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * REST controller exposing resume analysis, market trend radar, and dependency health check endpoints.
 */
@RestController
@RequestMapping("/api")
@Validated
public class ApiController {

    private static final Logger logger = LoggerFactory.getLogger(ApiController.class);

    private final AnalysisService analysisService;
    private final HistoryRepository historyRepository;
    private final SupabaseAuthService supabaseAuthService;
    private final RateLimiterService rateLimiterService;
    private final MarketTrendService marketTrendService;
    private final HttpClient httpClient;

    @Value("${supabase.url:}")
    private String supabaseUrl;

    public ApiController(
            AnalysisService analysisService,
            HistoryRepository historyRepository,
            SupabaseAuthService supabaseAuthService,
            RateLimiterService rateLimiterService,
            MarketTrendService marketTrendService
    ) {
        this.analysisService = analysisService;
        this.historyRepository = historyRepository;
        this.supabaseAuthService = supabaseAuthService;
        this.rateLimiterService = rateLimiterService;
        this.marketTrendService = marketTrendService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    /**
     * POST /api/analyze
     * Consumes {@code multipart/form-data}.
     * User authentication is verified exclusively via the {@code Authorization: Bearer <token>} header.
     */
    @PostMapping(value = "/analyze", consumes = "multipart/form-data")
    public ResponseEntity<AnalyzeApiResponse> analyze(
            @RequestParam("resume") MultipartFile resume,
            @RequestParam("jobDescription") @NotBlank(message = "Job description must not be empty.") @Size(max = 5000, message = "Job description exceeds maximum 5000 character limit.") String jobDescription,
            @RequestParam("apiKey") String apiKey,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            HttpServletRequest request
    ) throws IOException {

        // Rate limiting check per IP
        String clientIp = getClientIp(request);
        if (!rateLimiterService.tryConsume("ip:" + clientIp)) {
            throw new RateLimitExceededException("Rate limit exceeded for IP " + clientIp + ". Please wait before trying again.");
        }

        // Verify Bearer token server-side against Supabase
        Optional<String> verifiedUserId = supabaseAuthService.verifyToken(authHeader);

        if (verifiedUserId.isPresent()) {
            String userId = verifiedUserId.get();
            if (!rateLimiterService.tryConsume("user:" + userId)) {
                throw new RateLimitExceededException("Rate limit exceeded for user. Please wait before trying again.");
            }
        }

        String filename = resume != null ? resume.getOriginalFilename() : "resume.pdf";
        logger.info("Processing resume analysis for file: {}, user verified: {}", filename, verifiedUserId.isPresent());

        String resumeText = analysisService.extractResumeText(resume);
        AnalysisResponseDto analysisResult = analysisService.analyzeResume(resumeText, jobDescription, apiKey);

        int atsScore = (analysisResult.atsScore() != null) ? analysisResult.atsScore().score() : 75;

        boolean savedToHistory = false;
        if (verifiedUserId.isPresent()) {
            savedToHistory = historyRepository.saveAnalysisHistory(
                    verifiedUserId.get(),
                    filename,
                    jobDescription,
                    atsScore,
                    analysisResult
            );
        }

        AnalyzeApiResponse response = new AnalyzeApiResponse(
                analysisResult,
                savedToHistory,
                Instant.now().toString()
        );

        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/market-trends
     * Live Job Market Skill Radar. Consumes {@code multipart/form-data}.
     * Pulls CURRENTLY OPEN postings for the given target role from a live public jobs feed,
     * mines which skills are actually trending in demand right now, and cross-references
     * them against the uploaded resume — solving the real-world problem of resumes being
     * tailored to a single stale job posting instead of the live market.
     */
    @PostMapping(value = "/market-trends", consumes = "multipart/form-data")
    public ResponseEntity<MarketTrendResponseDto> marketTrends(
            @RequestParam("resume") MultipartFile resume,
            @RequestParam("role") @NotBlank(message = "Target role must not be empty.") @Size(max = 120, message = "Target role exceeds maximum 120 character limit.") String role,
            HttpServletRequest request
    ) throws IOException {

        String clientIp = getClientIp(request);
        if (!rateLimiterService.tryConsume("ip:" + clientIp)) {
            throw new RateLimitExceededException("Rate limit exceeded for IP " + clientIp + ". Please wait before trying again.");
        }

        logger.info("Running live market skill radar for role: {}", role);

        String resumeText = analysisService.extractResumeText(resume);
        MarketTrendResponseDto result = marketTrendService.analyzeMarketTrends(role, resumeText);

        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/health
     * Performs real reachability checks against external dependencies (Supabase, Gemini API).
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> healthMap = new LinkedHashMap<>();
        healthMap.put("status", "UP");
        healthMap.put("service", "AI Resume Analyzer Backend");
        healthMap.put("timestamp", Instant.now().toString());

        boolean supabaseUp = checkUrlReachability(supabaseUrl != null && !supabaseUrl.isBlank() ? supabaseUrl : "https://supabase.com");
        boolean geminiUp = checkUrlReachability("https://generativelanguage.googleapis.com");

        healthMap.put("dependencies", Map.of(
                "supabase", supabaseUp ? "UP" : "DOWN",
                "gemini", geminiUp ? "UP" : "DOWN"
        ));

        if (!supabaseUp || !geminiUp) {
            healthMap.put("status", "DEGRADED");
        }

        return ResponseEntity.ok(healthMap);
    }

    private boolean checkUrlReachability(String urlString) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(urlString))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() < 500;
        } catch (Exception e) {
            return false;
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
