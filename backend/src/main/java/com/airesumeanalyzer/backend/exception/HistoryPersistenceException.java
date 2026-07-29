package com.airesumeanalyzer.backend.exception;

public class HistoryPersistenceException extends RuntimeException {
    public HistoryPersistenceException(String message) {
        super(message);
    }

    public HistoryPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
