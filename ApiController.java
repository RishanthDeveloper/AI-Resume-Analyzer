package com.airesumeanalyzer.backend.controller;

import com.airesumeanalyzer.backend.service.AnalysisService;
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
 * REST controller exposing the resume analysis endpoint.
 * <p>
 * CORS is scoped to the local static frontend origins (e.g. VS Code
 * Live Server / `npx serve`). Update {@code origins} before deploying.
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(
        origins = {"http://localhost:5500", "http://127.0.0.1:5500"},
        allowedHeaders = "*",
        methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.OPTIONS}
)
public class ApiController {

    private final AnalysisService analysisService;

    public ApiController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    /**
     * POST /api/analyze
     * <p>
     * Consumes {@code multipart/form-data} containing:
     * <ul>
     *   <li>{@code resume} — the PDF file (required)</li>
     *   <li>{@code jobDescription} — target job description text (required)</li>
     *   <li>{@code apiKey} — the caller's Gemini API key (required)</li>
     * </ul>
     * Returns {@code { "analysisMarkdown": "...", "timestamp": "..." }} on success.
     */
    @PostMapping(value = "/analyze", consumes = "multipart/form-data")
    public ResponseEntity<Map<String, Object>> analyze(
            @RequestParam("resume") MultipartFile resume,
            @RequestParam("jobDescription") String jobDescription,
            @RequestParam("apiKey") String apiKey
    ) throws IOException {
        String resumeText = analysisService.extractResumeText(resume);
        String analysisMarkdown = analysisService.analyzeResume(resumeText, jobDescription, apiKey);

        Map<String, Object> responseBody = new LinkedHashMap<>();
        responseBody.put("analysisMarkdown", analysisMarkdown);
        responseBody.put("timestamp", Instant.now().toString());

        return ResponseEntity.ok(responseBody);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "OK"));
    }

    // -------------------------------------------------------------------
    // Exception handling — clean JSON error payloads with appropriate
    // HTTP status codes for PDF parsing failures and upstream API errors.
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
