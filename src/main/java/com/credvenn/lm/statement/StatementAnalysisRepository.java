package com.credvenn.lm.statement;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StatementAnalysisRepository extends JpaRepository<StatementAnalysis, String> {

    Optional<StatementAnalysis> findFirstByApplicationIdOrderByCreatedAtDesc(String applicationId);

    boolean existsByApplicationIdAndStatusIn(String applicationId, java.util.Collection<StatementAnalysisStatus> statuses);

    Optional<StatementAnalysis> findByExternalClientIdAndExternalDocumentId(String externalClientId, String externalDocumentId);
}
