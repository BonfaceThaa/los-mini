package com.credvenn.lm.application;

import com.credvenn.lm.common.exception.BadRequestException;
import com.credvenn.lm.document.DocumentService;
import com.credvenn.lm.security.SecretsEncryptionService;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicationStatementOtpService {

    private static final String OTP_OPEN_FAILURE_REASON = "PDF password did not open document";

    private final ApplicationStatementOtpRepository repository;
    private final SecretsEncryptionService secretsEncryptionService;
    private final DocumentService documentService;
    private final StatementPdfPasswordVerifier statementPdfPasswordVerifier;

    public ApplicationStatementOtpService(
            ApplicationStatementOtpRepository repository,
            SecretsEncryptionService secretsEncryptionService,
            DocumentService documentService,
            StatementPdfPasswordVerifier statementPdfPasswordVerifier) {
        this.repository = repository;
        this.secretsEncryptionService = secretsEncryptionService;
        this.documentService = documentService;
        this.statementPdfPasswordVerifier = statementPdfPasswordVerifier;
    }

    @Transactional
    public List<ApplicationStatementOtp> createInitialOtp(String tenantId, String applicationId, String otp) {
        return storeOtps(tenantId, applicationId, List.of(otp), ApplicationStatementOtpSource.APPLICATION_CREATE);
    }

    @Transactional
    public List<ApplicationStatementOtp> addOtps(String tenantId, String applicationId, List<String> otps) {
        return storeOtps(tenantId, applicationId, otps, ApplicationStatementOtpSource.MANUAL_ADD);
    }

    @Transactional(readOnly = true)
    public boolean hasAnyActiveOtp(String tenantId, String applicationId) {
        return repository.existsByTenantIdAndApplicationIdAndStatusIn(
                tenantId,
                applicationId,
                Set.of(
                        ApplicationStatementOtpStatus.PENDING,
                        ApplicationStatementOtpStatus.SUBMITTED,
                        ApplicationStatementOtpStatus.SUCCESSFUL));
    }

    @Transactional(readOnly = true)
    public boolean hasPendingOtp(String tenantId, String applicationId) {
        return repository.existsByTenantIdAndApplicationIdAndStatusIn(
                tenantId,
                applicationId,
                Set.of(ApplicationStatementOtpStatus.PENDING));
    }

    @Transactional(readOnly = true)
    public Optional<ResolvedOtp> findFirstPendingOtpThatOpensDocument(String tenantId, String applicationId, String documentId) {
        return resolveFirstPendingOtpThatOpensDocument(tenantId, applicationId, documentId, false);
    }

    @Transactional(readOnly = true)
    public List<StatementOtpView> listViews(String tenantId, String applicationId) {
        return repository.findAllByTenantIdAndApplicationIdOrderByCreatedAtAsc(tenantId, applicationId).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional
    public Optional<ResolvedOtp> reserveFirstPendingOtpThatOpensDocument(String tenantId, String applicationId, String documentId) {
        return resolveFirstPendingOtpThatOpensDocument(tenantId, applicationId, documentId, true);
    }

    @Transactional
    public Optional<ResolvedOtp> reserveNextOtp(String tenantId, String applicationId, String documentId) {
        return reserveFirstPendingOtpThatOpensDocument(tenantId, applicationId, documentId);
    }

    @Transactional(readOnly = true)
    public Optional<ResolvedOtp> findReservedOtp(String tenantId, String otpId) {
        return repository.findByIdAndTenantId(otpId, tenantId)
                .filter(otp -> otp.getStatus() == ApplicationStatementOtpStatus.SUBMITTED
                        || otp.getStatus() == ApplicationStatementOtpStatus.SUCCESSFUL)
                .map(otp -> new ResolvedOtp(
                        otp.getId(),
                        secretsEncryptionService.decrypt(otp.getOtpEncrypted()),
                        otp.getOtpMasked()));
    }

    @Transactional
    public void markSuccessful(String tenantId, String otpId, String documentId) {
        repository.findByIdAndTenantId(otpId, tenantId).ifPresent(otp -> {
            otp.setStatus(ApplicationStatementOtpStatus.SUCCESSFUL);
            otp.setUsedAt(Instant.now());
            otp.setTestedAt(Instant.now());
            otp.setUsedForDocumentId(documentId);
            otp.setFailureReason(null);
        });
    }

    @Transactional
    public void markFailed(String tenantId, String otpId, String reason) {
        repository.findByIdAndTenantId(otpId, tenantId).ifPresent(otp -> {
            otp.setStatus(ApplicationStatementOtpStatus.FAILED);
            otp.setTestedAt(Instant.now());
            otp.setFailureReason(trimToNull(reason));
        });
    }

    private Optional<ResolvedOtp> resolveFirstPendingOtpThatOpensDocument(
            String tenantId,
            String applicationId,
            String documentId,
            boolean mutateStatuses) {
        var document = documentService.getRequired(tenantId, documentId);
        if (!applicationId.equals(document.getApplicationId())) {
            throw new BadRequestException("Document does not belong to the loan request application");
        }
        byte[] pdfBytes = loadPdfBytes(tenantId, documentId);
        List<ApplicationStatementOtp> pendingOtps = repository.findAllByTenantIdAndApplicationIdAndStatusOrderByCreatedAtAsc(
                tenantId,
                applicationId,
                ApplicationStatementOtpStatus.PENDING);
        for (ApplicationStatementOtp otp : pendingOtps) {
            String otpValue = secretsEncryptionService.decrypt(otp.getOtpEncrypted());
            if (statementPdfPasswordVerifier.canOpen(pdfBytes, otpValue)) {
                if (mutateStatuses) {
                    otp.setStatus(ApplicationStatementOtpStatus.SUBMITTED);
                    otp.setUsedForDocumentId(documentId);
                    otp.setTestedAt(Instant.now());
                    otp.setFailureReason(null);
                }
                return Optional.of(new ResolvedOtp(otp.getId(), otpValue, otp.getOtpMasked()));
            }
            if (mutateStatuses) {
                otp.setStatus(ApplicationStatementOtpStatus.FAILED);
                otp.setUsedForDocumentId(documentId);
                otp.setTestedAt(Instant.now());
                otp.setFailureReason(OTP_OPEN_FAILURE_REASON);
            }
        }
        return Optional.empty();
    }

    private byte[] loadPdfBytes(String tenantId, String documentId) {
        try (var inputStream = documentService.loadContent(tenantId, documentId).getInputStream()) {
            return inputStream.readAllBytes();
        } catch (IOException ex) {
            throw new BadRequestException("Unable to read stored statement document");
        }
    }

    private StatementOtpView toView(ApplicationStatementOtp otp) {
        return new StatementOtpView(
                otp.getId(),
                secretsEncryptionService.decrypt(otp.getOtpEncrypted()),
                otp.getStatus(),
                otp.getSource(),
                otp.getCreatedAt(),
                otp.getTestedAt(),
                otp.getUsedAt(),
                otp.getFailureReason());
    }

    private List<ApplicationStatementOtp> storeOtps(
            String tenantId,
            String applicationId,
            List<String> otps,
            ApplicationStatementOtpSource source) {
        List<String> normalized = normalizeOtps(otps);
        List<ApplicationStatementOtp> saved = new ArrayList<>();
        for (String otp : normalized) {
            ApplicationStatementOtp row = new ApplicationStatementOtp();
            row.setTenantId(tenantId);
            row.setApplicationId(applicationId);
            row.setOtpEncrypted(secretsEncryptionService.encrypt(otp));
            row.setOtpMasked(maskOtp(otp));
            row.setStatus(ApplicationStatementOtpStatus.PENDING);
            row.setSource(source);
            saved.add(repository.save(row));
        }
        return saved;
    }

    private List<String> normalizeOtps(List<String> otps) {
        if (otps == null) {
            return List.of();
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String otp : otps) {
            String normalized = trimToNull(otp);
            if (normalized != null) {
                unique.add(normalized);
            }
        }
        return List.copyOf(unique);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String maskOtp(String value) {
        if (value == null || value.isBlank()) {
            return "****";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= 2) {
            return "*".repeat(trimmed.length());
        }
        return "*".repeat(Math.max(0, trimmed.length() - 2)) + trimmed.substring(trimmed.length() - 2);
    }

    public record ResolvedOtp(String otpId, String otpValue, String maskedOtp) {
    }

    public record StatementOtpView(
            String id,
            String otp,
            ApplicationStatementOtpStatus status,
            ApplicationStatementOtpSource source,
            Instant createdAt,
            Instant testedAt,
            Instant usedAt,
            String failureReason) {
    }
}
