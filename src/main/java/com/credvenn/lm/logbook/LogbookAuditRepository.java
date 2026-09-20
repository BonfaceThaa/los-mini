package com.credvenn.lm.logbook;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface LogbookAuditRepository extends JpaRepository<LogbookAudit,String> {
    List<LogbookAudit> findAllByTenantIdAndApplicationIdOrderByCreatedAtDesc(String tenantId, String applicationId);
}
