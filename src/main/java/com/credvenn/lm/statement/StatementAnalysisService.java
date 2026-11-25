package com.credvenn.lm.statement;

import com.credvenn.lm.application.ApplicationStatementOtpService;
import com.credvenn.lm.application.ApplicationStatus;
import com.credvenn.lm.application.ApplicationService;
import com.credvenn.lm.application.LoanRequestApplication;
import com.credvenn.lm.common.exception.BadRequestException;
import com.credvenn.lm.common.exception.NotFoundException;
import com.credvenn.lm.common.logging.LoggingContext;
import com.credvenn.lm.document.DocumentService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.credvenn.stmt.analyzer.core.AnalysisResult;
import com.credvenn.lm.subscription.StatementSubscriptionEligibilityService;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StatementAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(StatementAnalysisService.class);
    private static final String LEGACY_MANUAL_OVERRIDE_PROVIDER = "MANUAL_OVERRIDE";
    private static final String MPESA_STATEMENT_DOCUMENT_TYPE = "MPESA_STATEMENT";

    private final StatementAnalysisRepository statementAnalysisRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final StatementProviderRegistry statementProviderRegistry;
    private final ApplicationService applicationService;
    private final DocumentService documentService;
    private final StatementReviewService statementReviewService;
    private final CladfyStatementTransactionRepository cladfyStatementTransactionRepository;
    private final ApplicationStatementOtpService applicationStatementOtpService;
    private final ObjectMapper objectMapper;
    private final StatementSubscriptionEligibilityService statementEligibility;

    public StatementAnalysisService(
            StatementAnalysisRepository statementAnalysisRepository,
            ApplicationEventPublisher applicationEventPublisher,
            StatementProviderRegistry statementProviderRegistry,
            ApplicationService applicationService,
            DocumentService documentService,
            StatementReviewService statementReviewService,
            CladfyStatementTransactionRepository cladfyStatementTransactionRepository,
            ApplicationStatementOtpService applicationStatementOtpService,
            ObjectMapper objectMapper,
            StatementSubscriptionEligibilityService statementEligibility) {
        this.statementAnalysisRepository = statementAnalysisRepository;
        this.applicationEventPublisher = applicationEventPublisher;
        this.statementProviderRegistry = statementProviderRegistry;
        this.applicationService = applicationService;
        this.documentService = documentService;
        this.statementReviewService = statementReviewService;
        this.cladfyStatementTransactionRepository = cladfyStatementTransactionRepository;
        this.applicationStatementOtpService = applicationStatementOtpService;
        this.objectMapper = objectMapper;
        this.statementEligibility = statementEligibility;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public StatementDtos.StatementAssessmentResponse run(
            String tenantId,
            String applicationId,
            String documentId,
            String actor,
            String simulateOutcome) {
        try (LoggingContext.Scope ignored = LoggingContext.withTenantAndApplication(tenantId, applicationId)) {
            LoanRequestApplication application = applicationService.getRequired(tenantId, applicationId);
            assertStatementAnalysisAllowed(application);
            var document = documentService.getRequired(tenantId, documentId);
            if (!document.getApplicationId().equals(applicationId)) {
                throw new NotFoundException("Document does not belong to the loan request application");
            }
            String providerCode = normalizeSimulateOutcome(simulateOutcome) == null
                    ? statementProviderRegistry.providerForTenant(tenantId) : "SIMULATED";
            if (!"SIMULATED".equals(providerCode)) {
                statementProviderRegistry.byCode(providerCode);
            }
            if ("CLADFY".equals(providerCode)
                    && applicationStatementOtpService.findFirstPendingOtpThatOpensDocument(tenantId, applicationId, documentId).isEmpty()) {
                throw new BadRequestException("No pending statement OTP can open the statement document");
            }
            if (statementAnalysisRepository.existsByTenantIdAndApplicationIdAndStatusIn(tenantId, applicationId, Set.of(
                    StatementAnalysisStatus.PENDING,
                    StatementAnalysisStatus.IN_PROGRESS))) {
                log.info("Skipping statement analysis run because an analysis is already pending/in progress");
                return get(tenantId, applicationId);
            }
            StatementAnalysis analysis = new StatementAnalysis();
            analysis.setTenantId(tenantId);
            analysis.setApplicationId(applicationId);
            analysis.setSourceDocumentId(documentId);
            analysis.setProvider(providerCode);
            analysis.setStatus(StatementAnalysisStatus.PENDING);
            if ("INTERNAL_MPESA".equals(providerCode)) {
                statementEligibility.captureForSubmission(tenantId, analysis);
            }
            analysis = statementAnalysisRepository.save(analysis);
            log.info("Queued statement analysis for documentId={}", documentId);
            applicationEventPublisher.publishEvent(new StatementAnalysisRequestedEvent(
                    tenantId,
                    applicationId,
                    documentId,
                    analysis.getId(),
                    actor,
                    simulateOutcome));
            return buildResponse(tenantId, applicationId, Optional.of(analysis));
        }
    }

    @Transactional
    public boolean queueRetryIfEligible(String tenantId, String applicationId, String actor) {
        try (LoggingContext.Scope ignored = LoggingContext.withTenantAndApplication(tenantId, applicationId)) {
            LoanRequestApplication application = applicationService.getRequired(tenantId, applicationId);
            if (!isStatementAnalysisAllowed(application)) {
                log.info("Skipping statement analysis retry because application status={} is past statement processing", application.getStatus());
                return false;
            }
            if (statementAnalysisRepository.existsByTenantIdAndApplicationIdAndStatusIn(tenantId, applicationId, Set.of(
                    StatementAnalysisStatus.PENDING,
                    StatementAnalysisStatus.IN_PROGRESS))) {
                log.info("Skipping statement analysis retry because an analysis is already pending/in progress");
                return false;
            }
            Optional<StatementAnalysis> latestAnalysis = getLatestProviderAnalysis(tenantId, applicationId);
            if (latestAnalysis.isPresent() && latestAnalysis.get().getStatus() == StatementAnalysisStatus.PASSED) {
                log.info("Skipping statement analysis retry because latest provider analysis already passed");
                return false;
            }
            return documentService.findLatestByApplicationIdAndDocumentType(tenantId, applicationId, MPESA_STATEMENT_DOCUMENT_TYPE)
                    .filter(document -> isStatementOtpReadyForSubmission(tenantId, applicationId, document.getId(), null))
                    .map(document -> {
                        run(tenantId, applicationId, document.getId(), actor, null);
                        return true;
                    })
                    .orElse(false);
        }
    }

    @Transactional
    public StatementDtos.StatementAssessmentResponse manualPass(
            String tenantId,
            String applicationId,
            String actor,
            StatementDtos.ManualStatementPassRequest request) {
        try (LoggingContext.Scope ignored = LoggingContext.withTenantAndApplication(tenantId, applicationId)) {
            applicationService.getRequired(tenantId, applicationId);
            if (statementAnalysisRepository.existsByTenantIdAndApplicationIdAndStatusIn(tenantId, applicationId, Set.of(
                    StatementAnalysisStatus.PENDING,
                    StatementAnalysisStatus.IN_PROGRESS))) {
                throw new BadRequestException("A statement analysis is already pending or in progress");
            }
            Optional<StatementAnalysis> latestAnalysis = getLatestProviderAnalysis(tenantId, applicationId);
            if (latestAnalysis.isPresent() && latestAnalysis.get().getStatus() == StatementAnalysisStatus.PASSED) {
                log.info("Skipping manual statement pass because latest provider statement analysis is already passed");
                return buildResponse(tenantId, applicationId, latestAnalysis);
            }
            String reason = trimToDefault(request.summary(), "Manually approved after statement review");
            statementReviewService.recordDecision(
                    tenantId,
                    applicationId,
                    latestAnalysis.map(StatementAnalysis::getId).orElse(null),
                    StatementReviewDecision.APPROVED,
                    StatementReviewSource.USER,
                    actor,
                    reason);
            log.info("Recorded manual statement approval without overwriting provider analysis");
            applicationService.handleStatementPassed(tenantId, applicationId, actor);
            return buildResponse(tenantId, applicationId, latestAnalysis);
        }
    }

    @Transactional(readOnly = true)
    public StatementDtos.StatementAssessmentResponse get(String tenantId, String applicationId) {
        applicationService.getRequired(tenantId, applicationId);
        return buildResponse(tenantId, applicationId, Optional.empty());
    }

    private StatementDtos.StatementAssessmentResponse buildResponse(String tenantId, String applicationId, Optional<StatementAnalysis> analysisOverride) {
        StatementAnalysis analysis = analysisOverride.orElseGet(() -> getLatestProviderAnalysis(tenantId, applicationId).orElse(null));
        StatementReview review = resolveReview(tenantId, applicationId, analysis).orElse(null);
        if (analysis == null && review == null) {
            throw new NotFoundException("Statement analysis not found");
        }
        return new StatementDtos.StatementAssessmentResponse(
                analysis == null ? null : toAnalysisResponse(analysis),
                toReviewResponse(review),
                effectiveOutcome(analysis, review));
    }

    private Optional<StatementAnalysis> getLatestProviderAnalysis(String tenantId, String applicationId) {
        return statementAnalysisRepository.findAllByTenantIdAndApplicationIdOrderByCreatedAtDesc(tenantId, applicationId).stream()
                .filter(analysis -> !LEGACY_MANUAL_OVERRIDE_PROVIDER.equalsIgnoreCase(analysis.getProvider()))
                .findFirst();
    }

    private void assertStatementAnalysisAllowed(LoanRequestApplication application) {
        if (!isStatementAnalysisAllowed(application)) {
            throw new BadRequestException("Statement analysis is not allowed after status " + application.getStatus());
        }
    }

    private boolean isStatementAnalysisAllowed(LoanRequestApplication application) {
        ApplicationStatus status = application.getStatus();
        return status == null || switch (status) {
            case STATEMENT_VERIFIED,
                    OFFERS_READY,
                    OFFER_SELECTED,
                    CONSENT_CAPTURED,
                    INTERNAL_APPROVED,
                    FINERACT_LOAN_CREATED_PENDING_DEVICE,
                    DEVICE_ASSIGNED,
                    FINERACT_LOAN_ACTIVATED,
                    LOAN_CLOSED,
                    REJECTED -> false;
            default -> true;
        };
    }

    private boolean isStatementOtpReadyForSubmission(String tenantId, String applicationId, String documentId, String simulateOutcome) {
        if (normalizeSimulateOutcome(simulateOutcome) != null || !requiresStatementOtp(tenantId)) {
            return true;
        }
        return applicationStatementOtpService.findFirstPendingOtpThatOpensDocument(tenantId, applicationId, documentId).isPresent();
    }

    private Optional<StatementReview> resolveReview(String tenantId, String applicationId, StatementAnalysis analysis) {
        if (analysis != null) {
            Optional<StatementReview> reviewForAnalysis = statementReviewService.getLatestForAnalysis(tenantId, analysis.getId());
            if (reviewForAnalysis.isPresent()) {
                return reviewForAnalysis;
            }
            Optional<StatementReview> latestForApplication = statementReviewService.getLatestForApplication(tenantId, applicationId);
            if (latestForApplication.isPresent()) {
                StatementReview review = latestForApplication.get();
                if ((review.getStatementAnalysisId() == null || review.getStatementAnalysisId().isBlank())
                        && !review.getCreatedAt().isBefore(analysis.getCreatedAt())) {
                    return latestForApplication;
                }
            }
            return Optional.empty();
        }
        return statementReviewService.getLatestForApplication(tenantId, applicationId);
    }

    StatementDtos.StatementProviderAnalysisResponse toAnalysisResponse(StatementAnalysis analysis) {
        if ("INTERNAL_MPESA".equalsIgnoreCase(analysis.getProvider())) {
            AnalysisResult result = InternalStatementResultMapper.read(objectMapper, analysis);
            return toAnalysisResponse(analysis, null, List.of(), result);
        }
        StoredAnalysisResponse storedResponse = parseStoredAnalysisResponse(analysis.getRawProviderResponse()).orElse(null);
        return toAnalysisResponse(
                analysis,
                storedResponse,
                cladfyStatementTransactionRepository.findAllByStatementAnalysisIdOrderByCreatedAtAsc(analysis.getId()).stream()
                        .map(StatementAnalysisService::toTransactionResponse)
                        .toList(), null);
    }

    static StatementDtos.StatementProviderAnalysisResponse toAnalysisResponse(
            StatementAnalysis analysis,
            StoredAnalysisResponse storedResponse,
            List<StatementDtos.StatementTransactionResponse> transactions,
            AnalysisResult internalResult) {
        return new StatementDtos.StatementProviderAnalysisResponse(
                analysis.getId(),
                analysis.getApplicationId(),
                analysis.getProvider(),
                analysis.getStatus(),
                analysis.getSourceDocumentId(),
                analysis.getAverageMonthlyInflow(),
                analysis.getAverageMonthlyOutflow(),
                analysis.getAffordabilityScore(),
                analysis.getCreditScore(),
                analysis.getRiskTier(),
                analysis.getRecommendation(),
                analysis.getSummary(),
                storedResponse == null ? null : storedResponse.last_analyzed_on(),
                internalResult != null ? internalResult.totalInflow() : storedResponse == null ? null : storedResponse.total_in(),
                internalResult != null ? internalResult.totalOutflow() : storedResponse == null ? null : storedResponse.total_out(),
                toLoanSummaryResponses(storedResponse),
                transactions,
                analysis.getCreatedAt(),
                analysis.getUpdatedAt(),
                InternalStatementResultMapper.statement(internalResult),
                InternalStatementResultMapper.monthlySummary(internalResult),
                InternalStatementResultMapper.monthlyMetrics(internalResult),
                InternalStatementResultMapper.categories(analysis.getId(), internalResult),
                InternalStatementResultMapper.quality(internalResult),
                "INTERNAL_MPESA".equalsIgnoreCase(analysis.getProvider()) ? analysis.getBillingStatus() : null);
    }

    private Optional<StoredAnalysisResponse> parseStoredAnalysisResponse(String rawProviderResponse) {
        if (rawProviderResponse == null || rawProviderResponse.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(rawProviderResponse, StoredAnalysisResponse.class));
        } catch (JsonProcessingException ex) {
            return Optional.empty();
        }
    }

    private static List<StatementDtos.StatementLoanSummaryResponse> toLoanSummaryResponses(StoredAnalysisResponse storedResponse) {
        if (storedResponse == null || storedResponse.loans() == null) {
            return List.of();
        }
        return storedResponse.loans().stream()
                .map(loan -> new StatementDtos.StatementLoanSummaryResponse(
                        loan.lender(),
                        loan.amount_borrowed(),
                        loan.amount_repaid(),
                        loan.times_taken(),
                        loan.times_repaid(),
                        loan.status(),
                        loan.last_activity_date()))
                .toList();
    }

    static StatementDtos.StatementReviewResponse toReviewResponse(StatementReview review) {
        if (review == null) {
            return null;
        }
        return new StatementDtos.StatementReviewResponse(
                review.getId(),
                review.getApplicationId(),
                review.getStatementAnalysisId(),
                review.getDecision(),
                review.getDecisionSource(),
                review.getReason(),
                review.getReviewedBy(),
                review.getReviewedAt(),
                review.getCreatedAt(),
                review.getUpdatedAt());
    }

    static StatementDtos.StatementTransactionResponse toTransactionResponse(CladfyStatementTransaction transaction) {
        return new StatementDtos.StatementTransactionResponse(
                transaction.getId(),
                transaction.getExternalTransactionId(),
                transaction.getTransactionType(),
                transaction.getTransactionAmount(),
                transaction.getTransactionDate(),
                transaction.getNarration(),
                transaction.getBalance(),
                transaction.getCurrency());
    }

    static StatementEffectiveOutcome effectiveOutcome(StatementAnalysis analysis, StatementReview review) {
        if (review != null) {
            return switch (review.getDecision()) {
                case APPROVED -> StatementEffectiveOutcome.APPROVED;
                case REJECTED -> StatementEffectiveOutcome.REJECTED;
                case MANUAL_REVIEW_REQUIRED -> StatementEffectiveOutcome.MANUAL_REVIEW_REQUIRED;
            };
        }
        if (analysis == null) {
            return StatementEffectiveOutcome.NOT_AVAILABLE;
        }
        return switch (analysis.getStatus()) {
            case PENDING -> StatementEffectiveOutcome.PENDING;
            case IN_PROGRESS -> StatementEffectiveOutcome.IN_PROGRESS;
            case PROCESSING_FAILED -> StatementEffectiveOutcome.PROCESSING_FAILED;
            case PASSED -> StatementEffectiveOutcome.APPROVED;
            case FAILED -> StatementEffectiveOutcome.REJECTED;
            case MANUAL_REVIEW_REQUIRED -> StatementEffectiveOutcome.MANUAL_REVIEW_REQUIRED;
        };
    }

    private String normalizeSimulateOutcome(String simulateOutcome) {
        if (simulateOutcome == null) {
            return null;
        }
        String normalized = simulateOutcome.trim().toUpperCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    private String trimToDefault(String value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? defaultValue : trimmed;
    }

    private boolean requiresStatementOtp(String tenantId) {
        return "CLADFY".equalsIgnoreCase(statementProviderRegistry.providerForTenant(tenantId));
    }

    record StoredAnalysisResponse(
            String last_analyzed_on,
            java.math.BigDecimal total_in,
            java.math.BigDecimal total_out,
            List<StoredLoanSummary> loans) {
    }

    record StoredLoanSummary(
            String lender,
            java.math.BigDecimal amount_borrowed,
            java.math.BigDecimal amount_repaid,
            Integer times_taken,
            Integer times_repaid,
            String status,
            String last_activity_date) {
    }
}
