package com.credvenn.lm.subscription;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantSubscriptionRepository extends JpaRepository<TenantSubscription, String> {

    List<TenantSubscription> findAllByTenantIdOrderByCreatedAtDesc(String tenantId);

    Optional<TenantSubscription> findFirstByTenantIdAndStatusOrderByCreatedAtDesc(String tenantId, TenantSubscriptionStatus status);
}
