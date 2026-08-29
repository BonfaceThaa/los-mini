package com.credvenn.lm.application;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationStatementOtpRepository extends JpaRepository<ApplicationStatementOtp, String> {

    List<ApplicationStatementOtp> findAllByTenantIdAndApplicationIdOrderByCreatedAtAsc(String tenantId, String applicationId);

    List<ApplicationStatementOtp> findAllByApplicationIdOrderByCreatedAtAsc(String applicationId);

    boolean existsByTenantIdAndApplicationIdAndStatusIn(
            String tenantId,
            String applicationId,
            Collection<ApplicationStatementOtpStatus> statuses);

    Optional<ApplicationStatementOtp> findFirstByTenantIdAndApplicationIdAndStatusOrderByCreatedAtAsc(
            String tenantId,
            String applicationId,
            ApplicationStatementOtpStatus status);

    Optional<ApplicationStatementOtp> findByIdAndTenantId(String id, String tenantId);
}
