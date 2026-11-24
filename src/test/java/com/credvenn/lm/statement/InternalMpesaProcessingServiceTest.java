package com.credvenn.lm.statement;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.credvenn.lm.application.ApplicationStatementOtpService;
import com.credvenn.lm.application.LoanRequestApplication;
import com.credvenn.lm.document.ApplicationDocument;
import com.credvenn.lm.document.DocumentService;
import com.credvenn.stmt.analyzer.core.AnalysisResult;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.Optional;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ByteArrayResource;

class InternalMpesaProcessingServiceTest {
    private final DocumentService documents = mock(DocumentService.class);
    private final ApplicationStatementOtpService otps = mock(ApplicationStatementOtpService.class);
    private final InternalMpesaCompletionService completion = mock(InternalMpesaCompletionService.class);
    private final InternalMpesaProcessingService service = new InternalMpesaProcessingService(
            new InternalMpesaStatementAnalysisProvider(), completion, otps, documents);

    @Test
    void extractsStoredTenantDocumentWithActualAnalyzer() throws Exception {
        byte[] pdf = fixture();
        when(documents.loadContent("tenant-1", "doc-1")).thenReturn(new ByteArrayResource(pdf));
        service.process(work(), "officer");
        var result = ArgumentCaptor.forClass(AnalysisResult.class);
        verify(completion).complete(eq("tenant-1"), eq("analysis-1"), eq("officer"), result.capture(), isNull());
        assertFalse(result.getValue().transactions().isEmpty());
        assertEquals("analysis-1", result.getValue().correlationId());
        verifyNoInteractions(otps);
    }

    @Test
    void usesReservedPasswordForEncryptedDocument() throws Exception {
        byte[] encrypted;
        try (var pdf = Loader.loadPDF(fixture()); var output = new ByteArrayOutputStream()) {
            pdf.protect(new StandardProtectionPolicy("owner-password", "test-password", new AccessPermission()));
            pdf.save(output);
            encrypted = output.toByteArray();
        }
        when(documents.loadContent("tenant-1", "doc-1")).thenReturn(new ByteArrayResource(encrypted));
        when(otps.reserveFirstPendingOtpThatOpensDocument("tenant-1", "app-1", "doc-1"))
                .thenReturn(Optional.of(new ApplicationStatementOtpService.ResolvedOtp("otp-1", "test-password", "***rd")));
        service.process(work(), "officer");
        verify(completion).complete(eq("tenant-1"), eq("analysis-1"), eq("officer"), any(), eq("otp-1"));
    }

    @Test
    void malformedPdfProducesProcessingFailure() throws Exception {
        when(documents.loadContent("tenant-1", "doc-1")).thenReturn(new ByteArrayResource(new byte[] {1, 2}));
        service.process(work(), "officer");
        verify(completion).fail("tenant-1", "analysis-1", "officer", "MALFORMED_PDF", null);
        verify(completion, never()).complete(any(), any(), any(), any(), any());
    }

    @Test
    void encryptedPdfWithoutMatchingPasswordFailsWithoutReview() throws Exception {
        byte[] encrypted;
        try (var pdf = Loader.loadPDF(fixture()); var output = new ByteArrayOutputStream()) {
            pdf.protect(new StandardProtectionPolicy("owner-password", "test-password", new AccessPermission()));
            pdf.save(output);
            encrypted = output.toByteArray();
        }
        when(documents.loadContent("tenant-1", "doc-1")).thenReturn(new ByteArrayResource(encrypted));
        when(otps.reserveFirstPendingOtpThatOpensDocument("tenant-1", "app-1", "doc-1")).thenReturn(Optional.empty());
        service.process(work(), "officer");
        verify(completion).fail("tenant-1", "analysis-1", "officer", "INCORRECT_PASSWORD", null);
        verify(completion, never()).complete(any(), any(), any(), any(), any());
    }

    private byte[] fixture() throws Exception {
        try (var input = getClass().getResourceAsStream("/fixtures/balance-example.pdf")) {
            assertNotNull(input);
            return input.readAllBytes();
        }
    }

    private StatementSubmissionPreparationService.PreparedSubmission work() {
        var document = new ApplicationDocument();
        document.setTenantId("tenant-1");
        document.setApplicationId("app-1");
        document.setDocumentType("MPESA_STATEMENT");
        var application = new LoanRequestApplication();
        application.setTenantId("tenant-1");
        return new StatementSubmissionPreparationService.PreparedSubmission("analysis-1", "tenant-1", "app-1", "doc-1",
                application, document, null, false, false, Instant.now());
    }
}
