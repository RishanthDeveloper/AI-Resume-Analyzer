package com.airesumeanalyzer.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Bootstrap entry point for the AI Resume Analyzer backend.
 * <p>
 * Run locally with: {@code mvn spring-boot:run}
 * Server starts on port 8000 by default (or the $PORT environment variable on Render),
 * matching the frontend's API_URL configuration.
 */
@SpringBootApplication
public class ResumeAnalyzerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ResumeAnalyzerApplication.class, args);
    }
}
