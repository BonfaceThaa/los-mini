package com.credvenn.lm.statement;

import com.credvenn.lm.common.logging.LoggingContext;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class CladfyStatusPollingService {

    private static final Logger log = LoggerFactory.getLogger(CladfyStatusPollingService.class);

    static final Duration INITIAL_DELAY = Duration.ofMinutes(5);
    static final Duration RETRY_DELAY = Duration.ofMinutes(5);
    static final int BATCH_SIZE = 50;

    private final StatementAnalysisRepository statementAnalysisRepository;
    private final CladfyGateway cladfyGateway;
    private final CladfyAnalysisCompletionService completionService;
    private final TransactionTemplate transactionTemplate;

    public CladfyStatusPollingService(
            StatementAnalysisRepository statementAnalysisRepository,
            CladfyGateway cladfyGateway,
            CladfyAnalysisCompletionService completionService,
            PlatformTransactionManager transactionManager) {
        this.statementAnalysisRepository = statementAnalysisRepository;
        this.cladfyGateway = cladfyGateway;
        this.completionService = completionService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void scheduleInitialStatusCheck(StatementAnalysis analysis) {
        analysis.setNextStatusCheckAt(Instant.now().plus(INITIAL_DELAY));
        analysis.setLastStatusCheckAt(null);
        analysis.setStatusCheckAttempts(0);
        analysis.setCompletionSource(null);
        analysis.setCompletedAt(null);
    }

    public void pollDueAnalyses() {
        List<String> analysisIds = claimDueAnalyses(Instant.now());
        for (String analysisId : analysisIds) {
            pollAnalysis(analysisId);
        }
    }

    protected List<String> claimDueAnalyses(Instant now) {
        return transactionTemplate.execute(status -> {
            List<StatementAnalysis> due = statementAnalysisRepository.findDueCladfyStatusChecks(now, PageRequest.of(0, BATCH_SIZE));
            for (StatementAnalysis analysis : due) {
                analysis.setLastStatusCheckAt(now);
                analysis.setStatusCheckAttempts(analysis.getStatusCheckAttempts() + 1);
                analysis.setNextStatusCheckAt(now.plus(RETRY_DELAY));
                analysis.setProviderStatus("STATUS_POLLING");
            }
            return due.stream().map(StatementAnalysis::getId).toList();
        });
    }

    protected StatementAnalysis getAnalysisForPolling(String analysisId) {
        return transactionTemplate.execute(status -> statementAnalysisRepository.findById(analysisId).orElse(null));
    }

    protected void pollAnalysis(String analysisId) {
        StatementAnalysis analysis = getAnalysisForPolling(analysisId);
        if (analysis == null || analysis.getStatus() != StatementAnalysisStatus.IN_PROGRESS) {
            return;
        }
        try (LoggingContext.Scope ignored = LoggingContext.withTenantAndApplication(analysis.getTenantId(), analysis.getApplicationId())) {
            CladfyDtos.DocumentStatusResponse statusResponse = cladfyGateway.fetchDocumentStatus(analysis.getExternalDocumentId());
            String status = statusResponse == null || statusResponse.status() == null ? null : statusResponse.status().trim().toLowerCase();
            if ("analyzed".equals(status)) {
                completionService.completeAnalyzed(analysis, "cladfy-status-poller", "STATUS_POLLER", null);
                return;
            }
            if ("failed".equals(status)) {
                completionService.completeFailedStatus(analysis, statusResponse, "cladfy-status-poller", "STATUS_POLLER");
                return;
            }
            updateProgressStatus(analysis.getId(), statusResponse);
            log.info(
                    "Cladfy status poll deferred applicationId={} documentId={} providerStatus={} attempts={}",
                    analysis.getApplicationId(),
                    analysis.getExternalDocumentId(),
                    statusResponse == null ? null : statusResponse.status(),
                    analysis.getStatusCheckAttempts());
        } catch (RuntimeException ex) {
            log.warn(
                    "Cladfy status poll failed applicationId={} analysisId={} documentId={}",
                    analysis.getApplicationId(),
                    analysis.getId(),
                    analysis.getExternalDocumentId(),
                    ex);
        }
    }

    protected void updateProgressStatus(String analysisId, CladfyDtos.DocumentStatusResponse statusResponse) {
        transactionTemplate.executeWithoutResult(status -> {
            StatementAnalysis analysis = statementAnalysisRepository.findById(analysisId).orElse(null);
            if (analysis == null || analysis.getStatus() != StatementAnalysisStatus.IN_PROGRESS) {
                return;
            }
            analysis.setProviderStatus(statusResponse == null ? "status_unknown" : statusResponse.status());
            analysis.setRawProviderResponse("documentStatus=%s fetchedAt=%s".formatted(statusResponse, Instant.now()));
        });
    }

}
