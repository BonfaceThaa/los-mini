package com.credvenn.lm.scoring;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScorecardVersionRepository extends JpaRepository<ScorecardVersion, String> {
    Optional<ScorecardVersion> findByTenantIdAndOriginationProfileIdAndId(String tenantId, String profileId, String id);
    List<ScorecardVersion> findAllByTenantIdAndOriginationProfileIdOrderByVersionNumberDesc(String tenantId, String profileId);
    Optional<ScorecardVersion> findFirstByTenantIdAndOriginationProfileIdOrderByVersionNumberDesc(String tenantId, String profileId);
}
