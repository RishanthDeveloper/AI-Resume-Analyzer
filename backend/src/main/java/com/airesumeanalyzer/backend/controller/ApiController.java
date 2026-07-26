package com.airesumeanalyzer.backend.controller;

import com.airesumeanalyzer.backend.service.AnalysisService;
import com.airesumeanalyzer.backend.service.SupabaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST controller exposing resume analysis and health check endpoints.
 * <p>
 * TODO: Replace or supplement production Vercel domain in CORS configuration once deployed.
 * Example production origin: "https://resume-analyzer-zero-trust.vercel.app"
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(
        origins = {
                "http://localhost:5500",
                "http://127.0.0.1:5500",
                "http://localhost:3000",
                "http://127.0.0.1:3000",
                "http://localhost:8000",
                "http://127.0.0.1:8000"
                // TODO: Add your production Vercel frontend URL here, e.g.:
                // "https://resume-analyzer-zero-trust.vercel.app"
        },
        allowedHeaders = "*",
        methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.OPTIONS}
)
public class ApiController {

    private static final Logger logger = LoggerFactory.getLogger(ApiController.class);

    private final AnalysisService analysisService;
    private final SupabaseService supabaseService;

    public ApiController(AnalysisService analysisService, SupabaseService supabaseService) {
        this.analysisService = analysisService;
        this.supabaseService = supabaseService;
    }

    /**
     * POST /api/analyze
     * <p>
     * Consumes {@code multipart/form-data} containing:
     * <ul>
     *   <li>{@code resume} — PDF resume file (required)</li>
     *   <li>{@code jobDescription} — target job description text (required)</li>
     *   <li>{@code apiKey} — Gemini API key (required per-request)</li>
     *   <li>{@code userId} — optional Supabase user UUID for history persistence</li>
     * </ul>
     *
     * Returns structured JSON object containing all 5 analysis features.
     */
    @PostMapping(value = "/analyze", consumes = "multipart/form-data")
    public ResponseEntity<Map<String, Object>> analyze(
            @RequestParam("resume") MultipartFile resume,
            @RequestParam("jobDescription") String jobDescription,
            @RequestParam("apiKey") String apiKey,
            @RequestParam(value = "userId", required = false) String userId
    ) throws IOException {

        String filename = resume != null ? resume.getOriginalFilename() : "resume.pdf";
        logger.info("Received analysis request for file: {}, userId present: {}", filename, userId != null && !userId.isBlank());

        String resumeText = analysisService.extractResumeText(resume);
        Map<String, Object> analysisResult = analysisService.analyzeResume(resumeText, jobDescription, apiKey);

        int atsScore = extractAtsScore(analysisResult);

        boolean savedToHistory = false;
        if (userId != null && !userId.isBlank()) {
            savedToHistory = supabaseService.saveAnalysisHistory(userId, filename, jobDescription, atsScore, analysisResult);
        }

        Map<String, Object> responseBody = new LinkedHashMap<>();
        responseBody.put("analysis", analysisResult);
        responseBody.put("savedToHistory", savedToHistory);
        responseBody.put("timestamp", Instant.now().toString());

        return ResponseEntity.ok(responseBody);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "OK", "service", "AI Resume Analyzer Backend"));
    }

    private int extractAtsScore(Map<String, Object> analysisResult) {
        try {
            if (analysisResult.containsKey("atsScore") && analysisResult.get("atsScore") instanceof Map) {
                Map<?, ?> atsMap = (Map<?, ?>) analysisResult.get("atsScore");
                if (atsMap.containsKey("score") && atsMap.get("score") instanceof Number) {
                    return ((Number) atsMap.get("score")).intValue();
                }
            }
            if (analysisResult.containsKey("jobMatching") && analysisResult.get("jobMatching") instanceof Map) {
                Map<?, ?> matchMap = (Map<?, ?>) analysisResult.get("jobMatching");
                if (matchMap.containsKey("matchPercentage") && matchMap.get("matchPercentage") instanceof Number) {
                    return ((Number) matchMap.get("matchPercentage")).intValue();
                }
            }
        } catch (Exception e) {
            logger.warn("Could not extract ATS score integer for history summary", e);
        }
        return 75;
    }

    // -------------------------------------------------------------------
    // Exception handling
    // -------------------------------------------------------------------

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        return errorResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleFileTooLarge(MaxUploadSizeExceededException ex) {
        return errorResponse(HttpStatus.PAYLOAD_TOO_LARGE, "Uploaded file exceeds the 10MB limit.");
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<Map<String, Object>> handleIOException(IOException ex) {
        return errorResponse(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleUpstreamFailure(IllegalStateException ex) {
        return errorResponse(HttpStatus.BAD_GATEWAY, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error: " + ex.getMessage());
    }

    private ResponseEntity<Map<String, Object>> errorResponse(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", message);
        body.put("status", status.value());
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(status).body(body);
    }
}
