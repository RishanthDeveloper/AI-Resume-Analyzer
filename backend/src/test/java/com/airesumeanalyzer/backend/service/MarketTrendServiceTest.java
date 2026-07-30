package com.airesumeanalyzer.backend.service;

import com.airesumeanalyzer.backend.dto.MarketTrendResponseDto;
import com.airesumeanalyzer.backend.exception.MarketDataUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MarketTrendServiceTest {

    private HttpClient mockHttpClient;
    @SuppressWarnings("unchecked")
    private HttpResponse<String> mockHttpResponse = mock(HttpResponse.class);
    private MarketTrendService marketTrendService;

    @BeforeEach
    void setUp() {
        mockHttpClient = mock(HttpClient.class);
        marketTrendService = new MarketTrendService(mockHttpClient);
    }

    @Test
    void analyzeMarketTrends_EmptyRole_ThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> marketTrendService.analyzeMarketTrends("", "resume text"));
    }

    @Test
    void analyzeMarketTrends_ApiFailureStatus_ThrowsMarketDataUnavailableException() throws Exception {
        when(mockHttpResponse.statusCode()).thenReturn(500);
        doReturn(mockHttpResponse).when(mockHttpClient).send(any(), any());

        assertThrows(MarketDataUnavailableException.class, () -> marketTrendService.analyzeMarketTrends("Developer", "resume text"));
    }

    @Test
    void analyzeMarketTrends_ValidJsonResponse_ReturnsPopulatedDto() throws Exception {
        String sampleJson = """
                {
                  "jobs": [
                    {
                      "id": 1,
                      "title": "Senior Java Developer",
                      "company_name": "Tech Corp",
                      "url": "https://remotive.com/job/1",
                      "publication_date": "2026-07-29T10:00:00",
                      "tags": ["java", "spring boot", "docker"],
                      "description": "We need Java and Spring Boot experience with Docker."
                    }
                  ]
                }
                """;

        when(mockHttpResponse.statusCode()).thenReturn(200);
        when(mockHttpResponse.body()).thenReturn(sampleJson);
        doReturn(mockHttpResponse).when(mockHttpClient).send(any(), any());

        MarketTrendResponseDto dto = marketTrendService.analyzeMarketTrends("Java Developer", "I have Java experience.");

        assertNotNull(dto);
        assertEquals("Java Developer", dto.role());
        assertEquals(1, dto.jobsAnalyzed());
        assertFalse(dto.trendingSkills().isEmpty());
    }
}
