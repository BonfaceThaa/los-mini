package com.credvenn.lm.statement;

import com.credvenn.lm.application.ApplicationService;
import com.credvenn.lm.application.ApplicationStatementOtpService;
import com.credvenn.lm.application.LoanRequestApplication;
import com.credvenn.lm.common.exception.BadRequestException;
import com.credvenn.lm.common.exception.NotFoundException;
import com.credvenn.lm.document.ApplicationDocument;
import com.credvenn.lm.document.DocumentService;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StatementSubmissionPreparationService {

    private static final Duration RECOVERY_CLOCK_SKEW = Duration.ofMinutes(1);

    private final StatementAnalysisRepository statementAnalysisRepository;
    private final ApplicationService applicationService;
    private final DocumentService documentService;
    private final ApplicationStatementOtpService applicationStatementOtpService;

    public StatementSubmissionPreparationService(
            StatementAnalysisRepository statementAnalysisRepository,
            ApplicationService applicationService,
            DocumentService documentService,
            ApplicationStatementOtpService applicationStatementOtpService) {
        this.statementAnalysisRepository = statementAnalysisRepository;
        this.applicationService = applicationService;
        this.documentService = documentService;
        this.applicationStatementOtpService = applicationStatementOtpService;
    }

    @Transactional
    public PreparedSubmission prepare(
            String tenantId,
            String analysisId,
            String actor,
            String providerCode,
            boolean requiresOtp) {
        StatementAnalysis analysis = statementAnalysisRepository.findForSubmissionUpdate(analysisId, tenantId)
                .orElseThrow(() -> new NotFoundException("Statement analysis not found"));
        LoanRequestApplication application = applicationService.getRequired(tenantId, analysis.getApplicationId());
        ApplicationDocument document = documentService.getRequired(tenantId, analysis.getSourceDocumentId());

        if (!analysis.getApplicationId().equals(document.getApplicationId())) {
            throw new BadRequestException("Document does not belong to the loan request application");
        }
        if (analysis.getExternalDocumentId() != null && !analysis.getExternalDocumentId().isBlank()) {
            return PreparedSubmission.completed(analysis, application, document);
        }

        boolean recoveryAttempt = analysis.getStatus() == StatementAnalysisStatus.IN_PROGRESS;
        ApplicationStatementOtpService.ResolvedOtp resolvedOtp = requiresOtp
                ? resolveOtp(tenantId, analysis, document, recoveryAttempt)
                : null;

        if (!recoveryAttempt) {
            applicationService.handleStatementInProgress(tenantId, analysis.getApplicationId(), actor);
        }
        analysis.setProvider(providerCode);
        analysis.setStatus(StatementAnalysisStatus.IN_PROGRESS);
        if (resolvedOtp != null) {
            analysis.setStatementOtpId(resolvedOtp.otpId());
        }
        statementAnalysisRepository.save(analysis);

        Instant recoveryNotBefore = analysis.getUpdatedAt() == null
                ? Instant.now().minus(RECOVERY_CLOCK_SKEW)
                : analysis.getUpdatedAt().minus(RECOVERY_CLOCK_SKEW);
        return new PreparedSubmission(
                analysis.getId(),
                tenantId,
                analysis.getApplicationId(),
                analysis.getSourceDocumentId(),
                application,
                document,
                resolvedOtp,
                recoveryAttempt,
                false,
                recoveryNotBefore);
    }

    private ApplicationStatementOtpService.ResolvedOtp resolveOtp(
            String tenantId,
            StatementAnalysis analysis,
            ApplicationDocument document,
            boolean recoveryAttempt) {
        if (recoveryAttempt && analysis.getStatementOtpId() != null) {
            return applicationStatementOtpService.findReservedOtp(tenantId, analysis.getStatementOtpId())
                    .orElseThrow(() -> new BadRequestException("Reserved statement OTP is no longer available"));
        }
        return applicationStatementOtpService.reserveFirstPendingOtpThatOpensDocument(
                        tenantId,
                        analysis.getApplicationId(),
                        document.getId())
                .orElseThrow(() -> new BadRequestException("No pending statement OTP can open the statement document"));
    }

    public record PreparedSubmission(
            String analysisId,
            String tenantId,
            String applicationId,
            String documentId,
            LoanRequestApplication application,
            ApplicationDocument document,
            ApplicationStatementOtpService.ResolvedOtp resolvedOtp,
            boolean recoveryAttempt,
            boolean alreadySubmitted,
            Instant recoveryNotBefore) {

        static PreparedSubmission completed(
                StatementAnalysis analysis,
                LoanRequestApplication application,
                ApplicationDocument document) {
            return new PreparedSubmission(
                    analysis.getId(),
                    analysis.getTenantId(),
                    analysis.getApplicationId(),
                    analysis.getSourceDocumentId(),
                    application,
                    document,
                    null,
                    true,
                    true,
                    analysis.getUpdatedAt());
        }
    }
}
