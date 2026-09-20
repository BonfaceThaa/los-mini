package com.credvenn.lm.logbook;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface LogbookValuationRepository extends JpaRepository<LogbookValuation,String> {
    List<LogbookValuation> findAllByTenantIdAndApplicationIdOrderByRecordedVersionDesc(String tenantId, String applicationId);
    Optional<LogbookValuation> findByTenantIdAndApplicationIdAndId(String tenantId, String applicationId, String id);
}
