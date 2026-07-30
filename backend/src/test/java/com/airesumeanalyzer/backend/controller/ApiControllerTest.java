package com.airesumeanalyzer.backend.controller;

import com.airesumeanalyzer.backend.dto.AnalysisResponseDto;
import com.airesumeanalyzer.backend.dto.AnalysisResponseDto.*;
import com.airesumeanalyzer.backend.repository.HistoryRepository;
import com.airesumeanalyzer.backend.service.AnalysisService;
import com.airesumeanalyzer.backend.service.MarketTrendService;
import com.airesumeanalyzer.backend.service.RateLimiterService;
import com.airesumeanalyzer.backend.service.SupabaseAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AnalysisService analysisService;

    @MockBean
    private HistoryRepository historyRepository;

    @MockBean
    private SupabaseAuthService supabaseAuthService;

    @MockBean
    private RateLimiterService rateLimiterService;

    @MockBean
    private MarketTrendService marketTrendService;

    private AnalysisResponseDto sampleAnalysisDto;

    @BeforeEach
    void setUp() {
        sampleAnalysisDto = new AnalysisResponseDto(
                new AtsScoreDto(85, new AtsBreakdownDto(90, 80, 85), "Great match"),
                new SkillGapDto(List.of("Docker"), List.of("Java", "Spring Boot"), "Minor gap"),
                new SuggestionsDto(List.of(new LineRewriteDto("Built API", "Architected REST API", "Better impact")), List.of("Add metrics")),
                new JobMatchingDto(85, "High alignment", List.of("Java"), List.of("Docker")),
                List.of(new InterviewQuestionDto("Explain Spring IoC", "Core Java", "Discuss DI container"))
        );

        when(rateLimiterService.tryConsume(anyString())).thenReturn(true);
        when(analysisService.extractResumeText(any())).thenReturn("John Doe Java Resume");
        when(analysisService.analyzeResume(anyString(), anyString(), anyString())).thenReturn(sampleAnalysisDto);
    }

    @Test
    void analyze_UnauthenticatedRequest_SucceedsWithoutHistorySave() throws Exception {
        MockMultipartFile resume = new MockMultipartFile("resume", "resume.pdf", "application/pdf", "dummy pdf content".getBytes());

        mockMvc.perform(multipart("/api/analyze")
                        .file(resume)
                        .param("jobDescription", "Looking for Java Engineer with Spring Boot experience.")
                        .param("apiKey", "test-api-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savedToHistory").value(false))
                .andExpect(jsonPath("$.analysis.atsScore.score").value(85));
    }

    @Test
    void analyze_AuthenticatedValidToken_SucceedsWithHistorySave() throws Exception {
        String token = "valid-supabase-jwt-token";
        String verifiedUserId = "user-uuid-12345";

        when(supabaseAuthService.verifyToken("Bearer " + token)).thenReturn(Optional.of(verifiedUserId));
        when(historyRepository.saveAnalysisHistory(eq(verifiedUserId), anyString(), anyString(), anyInt(), any()))
                .thenReturn(true);

        MockMultipartFile resume = new MockMultipartFile("resume", "resume.pdf", "application/pdf", "dummy pdf content".getBytes());

        mockMvc.perform(multipart("/api/analyze")
                        .file(resume)
                        .param("jobDescription", "Looking for Java Engineer.")
                        .param("apiKey", "test-api-key")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savedToHistory").value(true))
                .andExpect(jsonPath("$.analysis.atsScore.score").value(85));
    }

    @Test
    void analyze_InvalidToken_SucceedsWithoutHistorySave() throws Exception {
        String token = "invalid-token";
        when(supabaseAuthService.verifyToken("Bearer " + token)).thenReturn(Optional.empty());

        MockMultipartFile resume = new MockMultipartFile("resume", "resume.pdf", "application/pdf", "dummy pdf content".getBytes());

        mockMvc.perform(multipart("/api/analyze")
                        .file(resume)
                        .param("jobDescription", "Looking for Java Engineer.")
                        .param("apiKey", "test-api-key")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savedToHistory").value(false));
    }

    @Test
    void analyze_BlankJobDescription_Returns400BadRequest() throws Exception {
        MockMultipartFile resume = new MockMultipartFile("resume", "resume.pdf", "application/pdf", "dummy pdf content".getBytes());

        mockMvc.perform(multipart("/api/analyze")
                        .file(resume)
                        .param("jobDescription", "   ")
                        .param("apiKey", "test-api-key"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void analyze_OversizedJobDescription_Returns400BadRequest() throws Exception {
        String oversizedJd = "A".repeat(5001);
        MockMultipartFile resume = new MockMultipartFile("resume", "resume.pdf", "application/pdf", "dummy pdf content".getBytes());

        mockMvc.perform(multipart("/api/analyze")
                        .file(resume)
                        .param("jobDescription", oversizedJd)
                        .param("apiKey", "test-api-key"))
                .andExpect(status().isBadRequest());
    }
}
