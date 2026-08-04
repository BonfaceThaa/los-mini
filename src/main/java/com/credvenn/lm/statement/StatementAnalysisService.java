package com.credvenn.lm.statement;

import com.credvenn.lm.application.ApplicationService;
import com.credvenn.lm.common.exception.BadRequestException;
import com.credvenn.lm.common.exception.NotFoundException;
import com.credvenn.lm.common.logging.LoggingContext;
import com.credvenn.lm.document.DocumentService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StatementAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(StatementAnalysisService.class);
    private static final String LEGACY_MANUAL_OVERRIDE_PROVIDER = "MANUAL_OVERRIDE";

    private final StatementAnalysisRepository statementAnalysisRepository;
    private final StatementAnalysisProcessingService processingService;
    private final StatementProviderRegistry statementProviderRegistry;
    private final ApplicationService applicationService;
    private final DocumentService documentService;
    private final StatementReviewService statementReviewService;
    private final CladfyStatementTransactionRepository cladfyStatementTransactionRepository;
    private final ObjectMapper objectMapper;

    public StatementAnalysisService(
            StatementAnalysisRepository statementAnalysisRepository,
            StatementAnalysisProcessingService processingService,
            StatementProviderRegistry statementProviderRegistry,
            ApplicationService applicationService,
            DocumentService documentService,
            StatementReviewService statementReviewService,
            CladfyStatementTransactionRepository cladfyStatementTransactionRepository,
            ObjectMapper objectMapper) {
        this.statementAnalysisRepository = statementAnalysisRepository;
        this.processingService = processingService;
        this.statementProviderRegistry = statementProviderRegistry;
        this.applicationService = applicationService;
        this.documentService = documentService;
        this.statementReviewService = statementReviewService;
        this.cladfyStatementTransactionRepository = cladfyStatementTransactionRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public StatementDtos.StatementAssessmentResponse run(
            String tenantId,
            String applicationId,
            String documentId,
            String actor,
            String simulateOutcome) {
        try (LoggingContext.Scope ignored = LoggingContext.withTenantAndApplication(tenantId, applicationId)) {
            var application = applicationService.getRequired(tenantId, applicationId);
            var document = documentService.getRequired(tenantId, documentId);
            if (!document.getApplicationId().equals(applicationId)) {
                throw new NotFoundException("Document does not belong to the loan request application");
            }
            if (requiresStatementOtp() && (application.getStatementOtp() == null || application.getStatementOtp().isBlank())) {
                throw new BadRequestException("Statement OTP is required before submitting the statement to the configured provider");
            }
            if (statementAnalysisRepository.existsByApplicationIdAndStatusIn(applicationId, Set.of(
                    StatementAnalysisStatus.PENDING,
                    StatementAnalysisStatus.IN_PROGRESS))) {
                log.info("Skipping statement analysis run because an analysis is already pending/in progress");
                return get(tenantId, applicationId);
            }
            StatementAnalysis analysis = new StatementAnalysis();
            analysis.setTenantId(tenantId);
            analysis.setApplicationId(applicationId);
            analysis.setSourceDocumentId(documentId);
            analysis.setProvider(normalizeSimulateOutcome(simulateOutcome) == null ? "QUEUED" : "SIMULATED");
            analysis.setStatus(StatementAnalysisStatus.PENDING);
            analysis = statementAnalysisRepository.save(analysis);
            log.info("Queued statement analysis for documentId={}", documentId);
            processingService.process(tenantId, applicationId, documentId, actor, simulateOutcome);
            return buildResponse(applicationId, Optional.of(analysis));
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
            if (statementAnalysisRepository.existsByApplicationIdAndStatusIn(applicationId, Set.of(
                    StatementAnalysisStatus.PENDING,
                    StatementAnalysisStatus.IN_PROGRESS))) {
                throw new BadRequestException("A statement analysis is already pending or in progress");
            }
            Optional<StatementAnalysis> latestAnalysis = getLatestProviderAnalysis(applicationId);
            if (latestAnalysis.isPresent() && latestAnalysis.get().getStatus() == StatementAnalysisStatus.PASSED) {
                log.info("Skipping manual statement pass because latest provider statement analysis is already passed");
                return buildResponse(applicationId, latestAnalysis);
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
            return buildResponse(applicationId, latestAnalysis);
        }
    }

    @Transactional(readOnly = true)
    public StatementDtos.StatementAssessmentResponse get(String tenantId, String applicationId) {
        applicationService.getRequired(tenantId, applicationId);
        return buildResponse(applicationId, Optional.empty());
    }

    private StatementDtos.StatementAssessmentResponse buildResponse(String applicationId, Optional<StatementAnalysis> analysisOverride) {
        StatementAnalysis analysis = analysisOverride.orElseGet(() -> getLatestProviderAnalysis(applicationId).orElse(null));
        StatementReview review = resolveReview(applicationId, analysis).orElse(null);
        if (analysis == null && review == null) {
            throw new NotFoundException("Statement analysis not found");
        }
        return new StatementDtos.StatementAssessmentResponse(
                analysis == null ? null : toAnalysisResponse(analysis),
                toReviewResponse(review),
                effectiveOutcome(analysis, review));
    }

    private Optional<StatementAnalysis> getLatestProviderAnalysis(String applicationId) {
        return statementAnalysisRepository.findAllByApplicationIdOrderByCreatedAtDesc(applicationId).stream()
                .filter(analysis -> !LEGACY_MANUAL_OVERRIDE_PROVIDER.equalsIgnoreCase(analysis.getProvider()))
                .findFirst();
    }

    private Optional<StatementReview> resolveReview(String applicationId, StatementAnalysis analysis) {
        if (analysis != null) {
            Optional<StatementReview> reviewForAnalysis = statementReviewService.getLatestForAnalysis(analysis.getId());
            if (reviewForAnalysis.isPresent()) {
                return reviewForAnalysis;
            }
            Optional<StatementReview> latestForApplication = statementReviewService.getLatestForApplication(applicationId);
            if (latestForApplication.isPresent()) {
                StatementReview review = latestForApplication.get();
                if ((review.getStatementAnalysisId() == null || review.getStatementAnalysisId().isBlank())
                        && !review.getCreatedAt().isBefore(analysis.getCreatedAt())) {
                    return latestForApplication;
                }
            }
            return Optional.empty();
        }
        return statementReviewService.getLatestForApplication(applicationId);
    }

    StatementDtos.StatementProviderAnalysisResponse toAnalysisResponse(StatementAnalysis analysis) {
        StoredAnalysisResponse storedResponse = parseStoredAnalysisResponse(analysis.getRawProviderResponse()).orElse(null);
        return toAnalysisResponse(
                analysis,
                storedResponse,
                cladfyStatementTransactionRepository.findAllByStatementAnalysisIdOrderByCreatedAtAsc(analysis.getId()).stream()
                        .map(StatementAnalysisService::toTransactionResponse)
                        .toList());
    }

    static StatementDtos.StatementProviderAnalysisResponse toAnalysisResponse(
            StatementAnalysis analysis,
            StoredAnalysisResponse storedResponse,
            List<StatementDtos.StatementTransactionResponse> transactions) {
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
                storedResponse == null ? null : storedResponse.total_in(),
                storedResponse == null ? null : storedResponse.total_out(),
                toLoanSummaryResponses(storedResponse),
                transactions,
                analysis.getCreatedAt(),
                analysis.getUpdatedAt());
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

    private boolean requiresStatementOtp() {
        return "CLADFY".equalsIgnoreCase(statementProviderRegistry.currentProvider().providerCode());
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
