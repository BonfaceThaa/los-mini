package com.credvenn.lm.statement;

import com.credvenn.lm.application.ApplicationStatementOtpService;
import com.credvenn.lm.document.DocumentService;
import com.credvenn.stmt.analyzer.core.AnalysisProcessingException;
import com.credvenn.stmt.analyzer.core.AnalysisResult;
import com.credvenn.stmt.analyzer.core.ProcessingErrorCode;
import com.credvenn.stmt.analyzer.core.extraction.ExtractionLimits;
import java.io.IOException;
import com.credvenn.lm.scoring.InitialAssessmentStartService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class InternalMpesaProcessingService {
    private static final Logger log = LoggerFactory.getLogger(InternalMpesaProcessingService.class);
    private final InternalMpesaStatementAnalysisProvider provider;
    private final InternalMpesaCompletionService completion;
    private final ApplicationStatementOtpService otps;
    private final DocumentService documents;
    private final InitialAssessmentStartService assessments;

    public InternalMpesaProcessingService(InternalMpesaStatementAnalysisProvider provider,
            InternalMpesaCompletionService completion, ApplicationStatementOtpService otps, DocumentService documents,
            InitialAssessmentStartService assessments) {
        this.provider = provider;
        this.completion = completion;
        this.otps = otps;
        this.documents = documents;
        this.assessments = assessments;
    }

    public void process(StatementSubmissionPreparationService.PreparedSubmission work, String actor) {
        String otpId = null;
        AnalysisResult result;
        try {
            if (!"MPESA_STATEMENT".equalsIgnoreCase(work.document().getDocumentType())) {
                throw new AnalysisProcessingException(ProcessingErrorCode.UNSUPPORTED_DOCUMENT_TYPE, work.analysisId());
            }
            byte[] pdf;
            try (var input = documents.loadContent(work.tenantId(), work.documentId()).getInputStream()) {
                pdf = input.readNBytes(ExtractionLimits.DEFAULT.maxBytes() + 1);
            }
            try {
                result = provider.analyze(pdf, null, work.analysisId());
            } catch (AnalysisProcessingException ex) {
                if (ex.code() != ProcessingErrorCode.INCORRECT_PASSWORD) throw ex;
                var otp = otps.reserveFirstPendingOtpThatOpensDocument(work.tenantId(), work.applicationId(), work.documentId())
                        .orElseThrow(() -> ex);
                otpId = otp.otpId();
                result = provider.analyze(pdf, otp.otpValue(), work.analysisId());
            }
        } catch (AnalysisProcessingException ex) {
            completion.fail(work.tenantId(), work.analysisId(), actor, ex.code().name(), otpId);
            return;
        } catch (IOException ex) {
            completion.fail(work.tenantId(), work.analysisId(), actor, "DOCUMENT_READ_FAILED", otpId);
            return;
        } catch (RuntimeException ex) {
            completion.fail(work.tenantId(), work.analysisId(), actor, "INTERNAL_PROCESSING_ERROR", otpId);
            return;
        }
        completion.complete(work.tenantId(), work.analysisId(), actor, result, otpId);
        if (!result.transactions().isEmpty()) {
            try {
                assessments.start(work.tenantId(), work.applicationId(), work.analysisId());
            } catch (RuntimeException ex) {
                // Extraction and billing already committed; an unavailable assessment stays in review.
                log.warn("Unable to start internal assessment for analysisId={}; manual review remains required", work.analysisId(), ex);
            }
        }
    }
}
