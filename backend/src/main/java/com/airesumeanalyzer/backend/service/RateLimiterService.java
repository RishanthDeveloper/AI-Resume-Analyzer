package com.airesumeanalyzer.backend.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory token bucket rate limiter service backed by Bucket4j.
 * Limiting is performed per client IP address and per authenticated user ID.
 * <p>
 * Note: Must be migrated to a distributed cache (e.g., Redis) prior to horizontal multi-node scaling.
 */
@Service
public class RateLimiterService {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * Attempts to consume 1 token for the specified key (IP or user ID).
     *
     * @param key client identifier (e.g., "ip:192.168.1.1" or "user:uuid")
     * @return true if token was consumed, false if rate limit exceeded
     */
    public boolean tryConsume(String key) {
        if (key == null || key.isBlank()) {
            return true;
        }
        Bucket bucket = buckets.computeIfAbsent(key, k -> createNewBucket());
        return bucket.tryConsume(1);
    }

    private Bucket createNewBucket() {
        // 10 requests per minute
        Bandwidth limit = Bandwidth.classic(10, Refill.greedy(10, Duration.ofMinutes(1)));
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }
}
