package com.airesumeanalyzer.backend.service;

import com.airesumeanalyzer.backend.exception.ResumeParsingException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.concurrent.*;

/**
 * Component responsible for extracting plain text from uploaded PDF files safely.
 * Includes timeout protection against malicious or decompression-bomb PDFs.
 */
@Component
public class PdfTextExtractor {

    private static final int TIMEOUT_SECONDS = 5;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public String extractResumeText(MultipartFile resumeFile) {
        if (resumeFile == null || resumeFile.isEmpty()) {
            throw new IllegalArgumentException("Uploaded resume file is empty.");
        }

        Future<String> future = executor.submit(() -> parsePdfBytes(resumeFile.getBytes()));

        try {
            return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new ResumeParsingException("PDF parsing timed out. The file may be complex or malformed.");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) cause;
            }
            if (cause instanceof ResumeParsingException) {
                throw (ResumeParsingException) cause;
            }
            throw new ResumeParsingException("Failed to parse PDF file: " + cause.getMessage(), cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResumeParsingException("PDF parsing thread was interrupted.", e);
        }
    }

    private String parsePdfBytes(byte[] bytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            if (document.isEncrypted()) {
                throw new IllegalArgumentException("Uploaded PDF is password-protected and cannot be parsed.");
            }

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);

            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException("No extractable text was found in the uploaded PDF.");
            }
            return text.trim();
        } catch (InvalidPasswordException e) {
            throw new IllegalArgumentException("Uploaded PDF is password-protected and cannot be parsed.", e);
        } catch (IOException e) {
            throw new ResumeParsingException("Failed to parse the uploaded PDF — the file may be corrupted: " + e.getMessage(), e);
        }
    }
}
