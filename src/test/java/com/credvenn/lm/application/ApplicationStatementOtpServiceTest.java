package com.credvenn.lm.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.credvenn.lm.document.ApplicationDocument;
import com.credvenn.lm.document.DocumentService;
import com.credvenn.lm.security.SecretsEncryptionService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

class ApplicationStatementOtpServiceTest {

    @Test
    void reserveFirstPendingOtpThatOpensDocumentMarksEarlierFailuresAndReturnsFirstMatch() throws Exception {
        TestContext context = new TestContext();
        ApplicationDocument document = document("doc-1", "app-1");
        ApplicationStatementOtp first = otp("otp-1", "tenant-1", "app-1", "enc-1", "***11");
        ApplicationStatementOtp second = otp("otp-2", "tenant-1", "app-1", "enc-2", "***22");
        byte[] pdfBytes = "pdf".getBytes(StandardCharsets.UTF_8);

        when(context.documentService.getRequired("tenant-1", "doc-1")).thenReturn(document);
        when(context.documentService.loadContent("tenant-1", "doc-1")).thenReturn(new ByteArrayResource(pdfBytes));
        when(context.repository.findAllByTenantIdAndApplicationIdAndStatusOrderByCreatedAtAsc(
                "tenant-1",
                "app-1",
                ApplicationStatementOtpStatus.PENDING)).thenReturn(List.of(first, second));
        when(context.secretsEncryptionService.decrypt("enc-1")).thenReturn("111111");
        when(context.secretsEncryptionService.decrypt("enc-2")).thenReturn("222222");
        when(context.verifier.canOpen(pdfBytes, "111111")).thenReturn(false);
        when(context.verifier.canOpen(pdfBytes, "222222")).thenReturn(true);

        Optional<ApplicationStatementOtpService.ResolvedOtp> resolved = context.service.reserveFirstPendingOtpThatOpensDocument(
                "tenant-1",
                "app-1",
                "doc-1");

        assertTrue(resolved.isPresent());
        assertEquals("otp-2", resolved.get().otpId());
        assertEquals(ApplicationStatementOtpStatus.FAILED, first.getStatus());
        assertEquals("PDF password did not open document", first.getFailureReason());
        assertEquals("doc-1", first.getUsedForDocumentId());
        assertNotNull(first.getTestedAt());
        assertEquals(ApplicationStatementOtpStatus.SUBMITTED, second.getStatus());
        assertEquals("doc-1", second.getUsedForDocumentId());
        assertNotNull(second.getTestedAt());
    }

    @Test
    void findFirstPendingOtpThatOpensDocumentDoesNotMutateStatuses() throws Exception {
        TestContext context = new TestContext();
        ApplicationDocument document = document("doc-1", "app-1");
        ApplicationStatementOtp pending = otp("otp-1", "tenant-1", "app-1", "enc-1", "***11");
        byte[] pdfBytes = "pdf".getBytes(StandardCharsets.UTF_8);

        when(context.documentService.getRequired("tenant-1", "doc-1")).thenReturn(document);
        when(context.documentService.loadContent("tenant-1", "doc-1")).thenReturn(new ByteArrayResource(pdfBytes));
        when(context.repository.findAllByTenantIdAndApplicationIdAndStatusOrderByCreatedAtAsc(
                "tenant-1",
                "app-1",
                ApplicationStatementOtpStatus.PENDING)).thenReturn(List.of(pending));
        when(context.secretsEncryptionService.decrypt("enc-1")).thenReturn("111111");
        when(context.verifier.canOpen(pdfBytes, "111111")).thenReturn(true);

        Optional<ApplicationStatementOtpService.ResolvedOtp> resolved = context.service.findFirstPendingOtpThatOpensDocument(
                "tenant-1",
                "app-1",
                "doc-1");

        assertTrue(resolved.isPresent());
        assertEquals(ApplicationStatementOtpStatus.PENDING, pending.getStatus());
        assertNull(pending.getTestedAt());
    }

    private static ApplicationDocument document(String id, String applicationId) {
        ApplicationDocument document = new ApplicationDocument();
        setField(document, ApplicationDocument.class, "id", id);
        document.setTenantId("tenant-1");
        document.setApplicationId(applicationId);
        document.setDocumentType("MPESA_STATEMENT");
        document.setOriginalFilename("statement.pdf");
        document.setStoredFilename("statement.pdf");
        document.setRelativePath("relative/path.pdf");
        document.setContentType("application/pdf");
        document.setFileSize(10L);
        document.setPublicUrl("/api/v1/documents/" + id + "/content");
        document.setCreatedBy("tester");
        return document;
    }

    private static ApplicationStatementOtp otp(String id, String tenantId, String applicationId, String encryptedOtp, String maskedOtp) {
        ApplicationStatementOtp otp = new ApplicationStatementOtp();
        setField(otp, ApplicationStatementOtp.class, "id", id);
        otp.setTenantId(tenantId);
        otp.setApplicationId(applicationId);
        otp.setOtpEncrypted(encryptedOtp);
        otp.setOtpMasked(maskedOtp);
        otp.setStatus(ApplicationStatementOtpStatus.PENDING);
        otp.setSource(ApplicationStatementOtpSource.MANUAL_ADD);
        return otp;
    }

    private static void setField(Object target, Class<?> type, String fieldName, Object value) {
        try {
            var field = type.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static final class TestContext {
        private final ApplicationStatementOtpRepository repository = mock(ApplicationStatementOtpRepository.class);
        private final SecretsEncryptionService secretsEncryptionService = mock(SecretsEncryptionService.class);
        private final DocumentService documentService = mock(DocumentService.class);
        private final StatementPdfPasswordVerifier verifier = mock(StatementPdfPasswordVerifier.class);
        private final ApplicationStatementOtpService service = new ApplicationStatementOtpService(
                repository,
                secretsEncryptionService,
                documentService,
                verifier);
    }
}

