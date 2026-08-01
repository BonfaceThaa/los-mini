package com.credvenn.lm.statement;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StatementReviewRepository extends JpaRepository<StatementReview, String> {

    Optional<StatementReview> findFirstByApplicationIdOrderByCreatedAtDesc(String applicationId);

    Optional<StatementReview> findFirstByStatementAnalysisIdOrderByCreatedAtDesc(String statementAnalysisId);
}
