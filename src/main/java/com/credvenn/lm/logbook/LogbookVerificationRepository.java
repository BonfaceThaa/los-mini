package com.credvenn.lm.logbook;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface LogbookVerificationRepository extends JpaRepository<LogbookVerification,String> {
    List<LogbookVerification> findAllByTenantIdAndApplicationIdOrderByRecordedVersionDesc(String tenantId, String applicationId);
}
