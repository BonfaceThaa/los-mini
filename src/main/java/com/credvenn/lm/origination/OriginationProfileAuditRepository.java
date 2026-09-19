package com.credvenn.lm.origination;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OriginationProfileAuditRepository extends JpaRepository<OriginationProfileAudit, String> {
    Page<OriginationProfileAudit> findAllByTenantIdAndProfileIdOrderByCreatedAtDesc(String tenantId, String profileId, Pageable pageable);
}
