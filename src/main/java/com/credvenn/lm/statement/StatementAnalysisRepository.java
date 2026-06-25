package com.credvenn.lm.statement;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StatementAnalysisRepository extends JpaRepository<StatementAnalysis, String> {

    Optional<StatementAnalysis> findFirstByApplicationIdOrderByCreatedAtDesc(String applicationId);

    boolean existsByApplicationIdAndStatusIn(String applicationId, java.util.Collection<StatementAnalysisStatus> statuses);

    Optional<StatementAnalysis> findByExternalClientIdAndExternalDocumentId(String externalClientId, String externalDocumentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select analysis
            from StatementAnalysis analysis
            where analysis.provider = 'CLADFY'
              and analysis.status = com.credvenn.lm.statement.StatementAnalysisStatus.IN_PROGRESS
              and analysis.externalDocumentId is not null
              and analysis.nextStatusCheckAt is not null
              and analysis.nextStatusCheckAt <= :now
            order by analysis.nextStatusCheckAt asc, analysis.createdAt asc
            """)
    List<StatementAnalysis> findDueCladfyStatusChecks(@Param("now") Instant now, Pageable pageable);
}
