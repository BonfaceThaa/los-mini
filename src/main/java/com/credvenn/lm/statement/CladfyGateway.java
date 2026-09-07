package com.credvenn.lm.statement;

import com.credvenn.lm.application.LoanRequestApplication;
import com.credvenn.lm.document.ApplicationDocument;
import java.time.Instant;
import java.util.Optional;

public interface CladfyGateway {

    StatementAnalysisSubmission submit(LoanRequestApplication application, ApplicationDocument document, String statementOtp);

    Optional<StatementAnalysisSubmission> recoverSubmission(
            LoanRequestApplication application,
            ApplicationDocument document,
            Instant notBefore);

    CladfyDtos.DocumentStatusResponse fetchDocumentStatus(String documentId);

    CladfyDtos.AnalysisResultsResponse fetchAnalysisResults(String clientId);

    CladfyDtos.CreditScoreResponse fetchCreditScore(String clientId);
}
