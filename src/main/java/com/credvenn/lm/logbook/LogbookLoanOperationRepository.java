package com.credvenn.lm.logbook;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
public interface LogbookLoanOperationRepository extends JpaRepository<LogbookLoanOperation,String> {
    Optional<LogbookLoanOperation> findByTenantIdAndApplicationIdAndKind(String tenantId,String applicationId,LogbookLoanOperation.Kind kind);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<LogbookLoanOperation> findForUpdateByTenantIdAndApplicationIdAndId(String tenantId,String applicationId,String id);
    List<LogbookLoanOperation> findAllByTenantIdAndApplicationIdOrderByCreatedAtAsc(String tenantId,String applicationId);
    List<LogbookLoanOperation> findTop20ByTenantIdAndStateOrderByCreatedAtAsc(String tenantId,LogbookLoanOperation.State state);
}
