package com.credvenn.lm.subscription;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface TenantSubscriptionRepository extends JpaRepository<TenantSubscription, String> {

    List<TenantSubscription> findAllByTenantIdOrderByCreatedAtDesc(String tenantId);

    Optional<TenantSubscription> findFirstByTenantIdAndStatusOrderByCreatedAtDesc(String tenantId, TenantSubscriptionStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TenantSubscription> findTopByTenantIdAndStatusOrderByCreatedAtDesc(String tenantId, TenantSubscriptionStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from TenantSubscription s where s.id = :id and s.tenantId = :tenantId")
    Optional<TenantSubscription> findByIdAndTenantIdForUpdate(@Param("id") String id, @Param("tenantId") String tenantId);
}
