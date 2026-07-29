package com.airesumeanalyzer.backend.service;

import com.airesumeanalyzer.backend.repository.HistoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Service to interface with Supabase REST API using the service_role key.
 * Implements HistoryRepository interface.
 */
@Service
public class SupabaseService implements HistoryRepository {

    private static final Logger logger = LoggerFactory.getLogger(SupabaseService.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${supabase.url:}")
    private String supabaseUrl;

    @Value("${supabase.service-key:}")
    private String supabaseServiceKey;

    public SupabaseService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public SupabaseService(HttpClient httpClient, ObjectMapper objectMapper, String supabaseUrl, String supabaseServiceKey) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.supabaseUrl = supabaseUrl;
        this.supabaseServiceKey = supabaseServiceKey;
    }

    @Override
    public boolean saveAnalysisHistory(String userId, String resumeFilename, String jobDescription, int atsScore, Object analysisJson) {
        if (supabaseUrl == null || supabaseUrl.isBlank() || supabaseServiceKey == null || supabaseServiceKey.isBlank()) {
            logger.warn("Supabase URL or Service Key is not configured. Skipping history persistence.");
            return false;
        }

        if (userId == null || userId.isBlank()) {
            logger.info("No authenticated user ID provided. Skipping history save.");
            return false;
        }

        try {
            String targetUrl = supabaseUrl.replaceAll("/+$", "") + "/rest/v1/analysis_history";

            Map<String, Object> payload = new HashMap<>();
            payload.put("user_id", userId);
            payload.put("resume_filename", resumeFilename != null ? resumeFilename : "resume.pdf");
            payload.put("job_description", jobDescription);
            payload.put("ats_score", atsScore);
            payload.put("analysis_json", analysisJson);

            String requestBody = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .header("apikey", supabaseServiceKey)
                    .header("Authorization", "Bearer " + supabaseServiceKey)
                    .header("Prefer", "return=minimal")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                logger.info("Successfully saved analysis history record to Supabase for user: {}", userId);
                return true;
            } else {
                logger.error("Failed to save history to Supabase. HTTP {}: {}", response.statusCode(), response.body());
                return false;
            }
        } catch (Exception e) {
            logger.error("Exception occurred while saving analysis history to Supabase", e);
            return false;
        }
    }
}
