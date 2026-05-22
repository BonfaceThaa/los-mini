package com.credvenn.lm.subscription;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantSubscriptionLedgerRepository extends JpaRepository<TenantSubscriptionLedger, String> {

    boolean existsBySubscriptionIdAndEntryTypeAndChargeTypeAndReferenceId(
            String subscriptionId,
            SubscriptionLedgerEntryType entryType,
            SubscriptionChargeType chargeType,
            String referenceId);

    List<TenantSubscriptionLedger> findAllBySubscriptionIdOrderByIdDesc(String subscriptionId);
}
