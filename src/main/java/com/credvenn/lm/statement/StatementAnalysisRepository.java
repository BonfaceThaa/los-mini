package com.credvenn.lm.statement;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StatementAnalysisRepository extends JpaRepository<StatementAnalysis, String> {

    Optional<StatementAnalysis> findByIdAndTenantId(String id, String tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select analysis
            from StatementAnalysis analysis
            where analysis.id = :id
              and analysis.tenantId = :tenantId
            """)
    Optional<StatementAnalysis> findForSubmissionUpdate(
            @Param("id") String id,
            @Param("tenantId") String tenantId);

    Optional<StatementAnalysis> findFirstByApplicationIdOrderByCreatedAtDesc(String applicationId);

    Optional<StatementAnalysis> findFirstByTenantIdAndApplicationIdOrderByCreatedAtDesc(String tenantId, String applicationId);

    List<StatementAnalysis> findAllByTenantIdAndApplicationIdOrderByCreatedAtDesc(String tenantId, String applicationId);

    boolean existsByTenantIdAndApplicationIdAndStatusIn(String tenantId, String applicationId, java.util.Collection<StatementAnalysisStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select a from StatementAnalysis a
            where a.tenantId = :tenantId and a.billingSubscriptionId = :subscriptionId
              and (a.status in :statuses or a.billingStatus in
                   (com.credvenn.lm.subscription.StatementBillingStatus.PENDING_FUNDS,
                    com.credvenn.lm.subscription.StatementBillingStatus.CURRENCY_MISMATCH))
              and a.billingAmount is not null
              and (a.billingStatus is null or a.billingStatus not in
                   (com.credvenn.lm.subscription.StatementBillingStatus.CHARGED,
                    com.credvenn.lm.subscription.StatementBillingStatus.WAIVED,
                    com.credvenn.lm.subscription.StatementBillingStatus.WAIVER_RESERVED))
            """)
    List<StatementAnalysis> findOutstandingForUpdate(@Param("tenantId") String tenantId,
            @Param("subscriptionId") String subscriptionId,
            @Param("statuses") java.util.Collection<StatementAnalysisStatus> statuses);

    Optional<StatementAnalysis> findByExternalClientIdAndExternalDocumentId(String externalClientId, String externalDocumentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select analysis
            from StatementAnalysis analysis
            where analysis.provider = 'CLADFY'
              and analysis.status = com.credvenn.lm.statement.StatementAnalysisStatus.IN_PROGRESS
              and analysis.externalDocumentId is not null
              and analysis.nextStatusCheckAt is not null
              and analysis.nextStatusCheckAt <= :now
            order by analysis.nextStatusCheckAt asc, analysis.createdAt asc
            """)
    List<StatementAnalysis> findDueCladfyStatusChecks(@Param("now") Instant now, Pageable pageable);
}
