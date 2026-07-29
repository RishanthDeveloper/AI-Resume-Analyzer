package com.airesumeanalyzer.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.Optional;

/**
 * Service to verify Supabase JWT Bearer tokens against the Supabase Auth API (`/auth/v1/user`).
 * <p>
 * Ensures user identity is verified server-side rather than trusting client-supplied user IDs.
 */
@Service
public class SupabaseAuthService {

    private static final Logger logger = LoggerFactory.getLogger(SupabaseAuthService.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${supabase.url:}")
    private String supabaseUrl;

    @Value("${supabase.anon-key:}")
    private String supabaseAnonKey;

    public SupabaseAuthService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public SupabaseAuthService(HttpClient httpClient, ObjectMapper objectMapper, String supabaseUrl, String supabaseAnonKey) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.supabaseUrl = supabaseUrl;
        this.supabaseAnonKey = supabaseAnonKey;
    }

    /**
     * Verifies a raw JWT Bearer token against Supabase Auth endpoint {@code GET /auth/v1/user}.
     *
     * @param bearerToken raw token string (with or without "Bearer " prefix)
     * @return Optional containing the verified user's UUID if valid, or Optional.empty() if invalid/missing
     */
    public Optional<String> verifyToken(String bearerToken) {
        if (bearerToken == null || bearerToken.isBlank()) {
            return Optional.empty();
        }

        String token = bearerToken.startsWith("Bearer ") ? bearerToken.substring(7).trim() : bearerToken.trim();
        if (token.isBlank()) {
            return Optional.empty();
        }

        if (supabaseUrl == null || supabaseUrl.isBlank()) {
            logger.warn("Supabase URL is not configured. Cannot verify authentication token.");
            return Optional.empty();
        }

        try {
            String targetUrl = supabaseUrl.replaceAll("/+$", "") + "/auth/v1/user";
            String apiKeyHeader = (supabaseAnonKey != null && !supabaseAnonKey.isBlank()) ? supabaseAnonKey : token;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .timeout(Duration.ofSeconds(5))
                    .header("apikey", apiKeyHeader)
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode idNode = root.path("id");
                if (!idNode.isMissingNode() && !idNode.asText().isBlank()) {
                    String verifiedUserId = idNode.asText().trim();
                    logger.info("Successfully verified Supabase token for user ID: {}", verifiedUserId);
                    return Optional.of(verifiedUserId);
                }
            } else {
                logger.warn("Supabase token verification failed with HTTP status: {}", response.statusCode());
            }
        } catch (Exception e) {
            logger.error("Error occurred while verifying Supabase token", e);
        }

        return Optional.empty();
    }
}
