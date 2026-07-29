package com.airesumeanalyzer.backend.repository;

public interface HistoryRepository {
    boolean saveAnalysisHistory(String userId, String resumeFilename, String jobDescription, int atsScore, Object analysisJson);
}
