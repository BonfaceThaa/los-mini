package com.credvenn.lm.statement;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CladfyStatementTransactionRepository extends JpaRepository<CladfyStatementTransaction, String> {

    void deleteAllByStatementAnalysisId(String statementAnalysisId);

    List<CladfyStatementTransaction> findAllByStatementAnalysisIdOrderByCreatedAtAsc(String statementAnalysisId);
}
