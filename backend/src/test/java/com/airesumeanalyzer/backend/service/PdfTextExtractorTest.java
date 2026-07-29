package com.airesumeanalyzer.backend.service;

import com.airesumeanalyzer.backend.exception.ResumeParsingException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class PdfTextExtractorTest {

    private PdfTextExtractor pdfTextExtractor;

    @BeforeEach
    void setUp() {
        pdfTextExtractor = new PdfTextExtractor();
    }

    @Test
    void extractResumeText_ValidPdf_ReturnsExtractedText() throws IOException {
        byte[] pdfBytes = createSamplePdf("John Doe - Senior Java Engineer with Spring Boot experience.");
        MockMultipartFile file = new MockMultipartFile("resume", "resume.pdf", "application/pdf", pdfBytes);

        String text = pdfTextExtractor.extractResumeText(file);
        assertNotNull(text);
        assertTrue(text.contains("John Doe"));
        assertTrue(text.contains("Spring Boot"));
    }

    @Test
    void extractResumeText_EmptyFile_ThrowsIllegalArgumentException() {
        MockMultipartFile file = new MockMultipartFile("resume", "empty.pdf", "application/pdf", new byte[0]);
        assertThrows(IllegalArgumentException.class, () -> pdfTextExtractor.extractResumeText(file));
    }

    @Test
    void extractResumeText_NullFile_ThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> pdfTextExtractor.extractResumeText(null));
    }

    @Test
    void extractResumeText_CorruptedFile_ThrowsResumeParsingException() {
        MockMultipartFile file = new MockMultipartFile("resume", "corrupt.pdf", "application/pdf", "not a pdf file content".getBytes());
        assertThrows(ResumeParsingException.class, () -> pdfTextExtractor.extractResumeText(file));
    }

    @Test
    void extractResumeText_EncryptedPdf_ThrowsIllegalArgumentException() throws IOException {
        byte[] encryptedBytes = createEncryptedPdf("Secret Content");
        MockMultipartFile file = new MockMultipartFile("resume", "encrypted.pdf", "application/pdf", encryptedBytes);
        assertThrows(IllegalArgumentException.class, () -> pdfTextExtractor.extractResumeText(file));
    }

    private byte[] createSamplePdf(String contentText) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.beginText();
                contentStream.newLineAtOffset(100, 700);
                contentStream.showText(contentText);
                contentStream.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    private byte[] createEncryptedPdf(String contentText) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.beginText();
                contentStream.newLineAtOffset(100, 700);
                contentStream.showText(contentText);
                contentStream.endText();
            }
            AccessPermission ap = new AccessPermission();
            StandardProtectionPolicy spp = new StandardProtectionPolicy("ownerpass", "userpass", ap);
            spp.setEncryptionKeyLength(128);
            spp.setPermissions(ap);
            document.protect(spp);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }
}
