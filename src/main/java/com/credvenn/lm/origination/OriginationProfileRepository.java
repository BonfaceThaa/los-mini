package com.credvenn.lm.origination;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OriginationProfileRepository extends JpaRepository<OriginationProfile, String> {
    // Locking reads see committed state even if the surrounding MariaDB transaction has an older snapshot.
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_READ)
    Optional<OriginationProfile> findForApplicationByTenantIdAndCodeIgnoreCase(String tenantId, String code);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_READ)
    Optional<OriginationProfile> findForApplicationByTenantIdAndId(String tenantId, String id);
    Optional<OriginationProfile> findByTenantIdAndCodeIgnoreCase(String tenantId, String code);
    Optional<OriginationProfile> findByTenantIdAndId(String tenantId, String id);
    boolean existsByTenantIdAndCodeIgnoreCase(String tenantId, String code);
    List<OriginationProfile> findAllByTenantIdOrderByDisplayNameAsc(String tenantId);
    List<OriginationProfile> findAllByTenantIdAndActiveOrderByDisplayNameAsc(String tenantId, boolean active);
}
