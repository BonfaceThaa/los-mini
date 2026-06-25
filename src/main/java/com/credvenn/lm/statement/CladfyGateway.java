package com.credvenn.lm.statement;

import com.credvenn.lm.application.LoanRequestApplication;
import com.credvenn.lm.document.ApplicationDocument;

public interface CladfyGateway {

    StatementAnalysisSubmission submit(LoanRequestApplication application, ApplicationDocument document);

    CladfyDtos.DocumentStatusResponse fetchDocumentStatus(String documentId);

    CladfyDtos.AnalysisResultsResponse fetchAnalysisResults(String clientId);

    CladfyDtos.CreditScoreResponse fetchCreditScore(String clientId);
}
