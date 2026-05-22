package com.credvenn.lm.application;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;

public interface ApplicationStatusHistoryRepository extends JpaRepository<ApplicationStatusHistory, Long> {

    List<ApplicationStatusHistory> findAllByApplicationIdOrderByIdAsc(String applicationId);

    @Query("""
            select count(distinct h.applicationId)
            from ApplicationStatusHistory h, LoanRequestApplication a
            where h.applicationId = a.id
              and a.tenantId = :tenantId
              and h.toStatus = :toStatus
              and h.createdAt >= :start
              and h.createdAt < :end
            """)
    long countDistinctApplicationIdByTenantIdAndToStatusAndCreatedAtBetween(
            @Param("tenantId") String tenantId,
            @Param("toStatus") String toStatus,
            @Param("start") Instant start,
            @Param("end") Instant end);
}
