package com.credvenn.lm.application;

import com.credvenn.lm.security.SecretsEncryptionService;
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

    private final ApplicationStatementOtpRepository repository;
    private final SecretsEncryptionService secretsEncryptionService;

    public ApplicationStatementOtpService(
            ApplicationStatementOtpRepository repository,
            SecretsEncryptionService secretsEncryptionService) {
        this.repository = repository;
        this.secretsEncryptionService = secretsEncryptionService;
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
    public List<StatementOtpView> listViews(String tenantId, String applicationId) {
        return repository.findAllByTenantIdAndApplicationIdOrderByCreatedAtAsc(tenantId, applicationId).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional
    public Optional<ResolvedOtp> reserveNextOtp(String tenantId, String applicationId, String documentId) {
        Optional<ApplicationStatementOtp> next = repository.findFirstByTenantIdAndApplicationIdAndStatusOrderByCreatedAtAsc(
                tenantId,
                applicationId,
                ApplicationStatementOtpStatus.PENDING);
        if (next.isEmpty()) {
            return Optional.empty();
        }
        ApplicationStatementOtp otp = next.get();
        otp.setStatus(ApplicationStatementOtpStatus.SUBMITTED);
        otp.setUsedForDocumentId(documentId);
        otp.setTestedAt(Instant.now());
        otp.setFailureReason(null);
        return Optional.of(new ResolvedOtp(
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
