package com.credvenn.lm.subscription;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface TenantSubscriptionLedgerRepository extends JpaRepository<TenantSubscriptionLedger, String> {

    boolean existsBySubscriptionIdAndEntryTypeAndChargeTypeAndReferenceId(
            String subscriptionId,
            SubscriptionLedgerEntryType entryType,
            SubscriptionChargeType chargeType,
            String referenceId);

    List<TenantSubscriptionLedger> findAllBySubscriptionIdOrderByIdDesc(String subscriptionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select l from TenantSubscriptionLedger l, StatementAnalysis a
            where l.tenantId = :tenantId and a.tenantId = :tenantId
              and a.sourceDocumentId = :documentId and l.referenceId = a.id
              and l.entryType = com.credvenn.lm.subscription.SubscriptionLedgerEntryType.DEBIT
              and l.chargeType = com.credvenn.lm.subscription.SubscriptionChargeType.STATEMENT_SUCCESS
              and l.referenceType = com.credvenn.lm.subscription.SubscriptionReferenceType.STATEMENT_ANALYSIS
            """)
    List<TenantSubscriptionLedger> findDocumentChargesForUpdate(@Param("tenantId") String tenantId, @Param("documentId") String documentId);
}
