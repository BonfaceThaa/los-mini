package com.credvenn.lm.tenant;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantBrandingRepository extends JpaRepository<TenantBranding, String> {

    Optional<TenantBranding> findByTenantId(String tenantId);
}
