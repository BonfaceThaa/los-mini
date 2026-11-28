package com.credvenn.lm.scoring;

import com.credvenn.lm.common.exception.ConflictException;
import com.credvenn.lm.common.exception.NotFoundException;
import com.credvenn.lm.statement.StatementAnalysisRepository;
import com.credvenn.lm.statement.StatementAnalysisStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Starts the internal assessment only after extraction and billing have committed. */
@Service @RequiredArgsConstructor
public class InitialAssessmentStartService {
    private final AssessmentSnapshotService snapshots;
    private final StatementAnalysisRepository statements;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String start(String tenantId, String applicationId, String analysisId) {
        var analysis = statements.findByIdAndTenantId(analysisId, tenantId)
                .orElseThrow(() -> new NotFoundException("Statement analysis not found"));
        if (!applicationId.equals(analysis.getApplicationId()) || !"INTERNAL_MPESA".equals(analysis.getProvider())
                || analysis.getStatus() != StatementAnalysisStatus.MANUAL_REVIEW_REQUIRED)
            throw new ConflictException("A completed internal statement extraction is required");
        return snapshots.captureInitial(tenantId, applicationId, analysisId, "initial:" + analysisId);
    }
}
