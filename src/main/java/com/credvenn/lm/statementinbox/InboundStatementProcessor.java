package com.credvenn.lm.statementinbox;

import com.credvenn.lm.application.ApplicationStatus;
import com.credvenn.lm.application.LoanRequestApplication;
import com.credvenn.lm.application.LoanRequestApplicationRepository;
import com.credvenn.lm.common.exception.NotFoundException;
import com.credvenn.lm.common.logging.LoggingContext;
import com.credvenn.lm.document.DocumentService;
import java.io.InputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InboundStatementProcessor {

    private static final Logger log = LoggerFactory.getLogger(InboundStatementProcessor.class);

    private static final Set<ApplicationStatus> MATCHABLE_STATUSES = Set.of(
            ApplicationStatus.SUBMITTED,
            ApplicationStatus.PENDING_KYC,
            ApplicationStatus.KYC_IN_PROGRESS,
            ApplicationStatus.KYC_MANUAL_REVIEW,
            ApplicationStatus.KYC_PASSED_CLIENT_CREATED,
            ApplicationStatus.STATEMENT_PENDING);

    private final InboundStatementReceiptRepository receiptRepository;
    private final TenantStatementInboxRepository inboxRepository;
    private final InboundStatementFilenameParser filenameParser;
    private final LoanRequestApplicationRepository applicationRepository;
    private final DocumentService documentService;
    private final InboundStatementStorageService inboundStatementStorageService;

    public InboundStatementProcessor(
            InboundStatementReceiptRepository receiptRepository,
            TenantStatementInboxRepository inboxRepository,
            InboundStatementFilenameParser filenameParser,
            LoanRequestApplicationRepository applicationRepository,
            DocumentService documentService,
            InboundStatementStorageService inboundStatementStorageService) {
        this.receiptRepository = receiptRepository;
        this.inboxRepository = inboxRepository;
        this.filenameParser = filenameParser;
        this.applicationRepository = applicationRepository;
        this.documentService = documentService;
        this.inboundStatementStorageService = inboundStatementStorageService;
    }

    @Async
    @Transactional
    public void process(String receiptId, String actor) {
        InboundStatementReceipt receipt = receiptRepository.findById(receiptId)
                .orElseThrow(() -> new NotFoundException("Inbound statement receipt not found"));
        try (LoggingContext.Scope ignored = LoggingContext.withApplication(receiptId)) {
            log.info("Starting inbound statement processing destinationEmail={} filename={}",
                    receipt.getDestinationEmail(),
                    receipt.getOriginalFilename());
            receipt.setMatchStatus(InboundStatementMatchStatus.PROCESSING);
            receipt.setProcessingStartedAt(Instant.now());

            TenantStatementInbox inbox = inboxRepository.findByEmailAddressIgnoreCaseAndActiveTrue(receipt.getDestinationEmail())
                    .orElseThrow(() -> new NotFoundException("No tenant statement inbox matched the destination email"));
            receipt.setTenantId(inbox.getTenantId());

            String phoneToken = filenameParser.extractPhoneToken(receipt.getOriginalFilename());
            receipt.setExtractedPhoneToken(phoneToken);

            List<LoanRequestApplication> candidates = applicationRepository
                    .findAllByTenantIdAndStatusInAndCreatedAtAfterOrderByCreatedAtDesc(
                            inbox.getTenantId(),
                            MATCHABLE_STATUSES,
                            Instant.now().minus(1, ChronoUnit.HOURS));
            List<LoanRequestApplication> matches = candidates.stream()
                    .filter(application -> phoneMatches(phoneToken, application.getPhoneNumber()))
                    .toList();

            log.info("Inbound statement candidateCount={} matchCount={} phoneToken={}",
                    candidates.size(), matches.size(), maskPhoneToken(phoneToken));

            if (matches.isEmpty()) {
                receipt.setMatchStatus(InboundStatementMatchStatus.UNMATCHED);
                receipt.setFailureReason("No recent application matched the phone token extracted from the filename");
                receipt.setProcessedAt(Instant.now());
                log.warn("Inbound statement could not be matched to a recent application");
                return;
            }

            if (matches.size() > 1) {
                receipt.setMatchStatus(InboundStatementMatchStatus.AMBIGUOUS);
                receipt.setFailureReason("Multiple recent applications matched the phone token extracted from the filename");
                receipt.setProcessedAt(Instant.now());
                log.warn("Inbound statement matched multiple recent applications and requires manual review");
                return;
            }

            LoanRequestApplication application = matches.get(0);
            attachAndTrigger(receipt, application, actor, InboundStatementMatchStatus.MATCHED, null);
        } catch (Exception ex) {
            receipt.setMatchStatus(InboundStatementMatchStatus.FAILED);
            receipt.setBackgroundError(ex.getMessage());
            receipt.setProcessedAt(Instant.now());
            log.error("Inbound statement processing failed", ex);
        }
    }

    @Transactional
    public InboundStatementReceipt resolveReceipt(String tenantId, String receiptId, String applicationId, String actor, String notes) {
        InboundStatementReceipt receipt = receiptRepository.findByIdAndTenantId(receiptId, tenantId)
                .orElseThrow(() -> new NotFoundException("Inbound statement receipt not found"));
        LoanRequestApplication application = applicationRepository.findByIdAndTenantId(applicationId, tenantId)
                .orElseThrow(() -> new NotFoundException("Loan request application not found"));
        if (!MATCHABLE_STATUSES.contains(application.getStatus())) {
            throw new com.credvenn.lm.common.exception.BadRequestException(
                    "Receipt can only be resolved to applications in submitted or KYC states");
        }
        attachAndTrigger(receipt, application, actor, InboundStatementMatchStatus.MANUALLY_RESOLVED, notes);
        receipt.setResolvedBy(actor);
        receipt.setResolvedAt(Instant.now());
        return receipt;
    }

    private void attachAndTrigger(
            InboundStatementReceipt receipt,
            LoanRequestApplication application,
            String actor,
            InboundStatementMatchStatus status,
            String notes) {
        try (LoggingContext.Scope ignored = LoggingContext.withTenantAndApplication(application.getTenantId(), application.getId())) {
            try {
                try (InputStream inputStream = inboundStatementStorageService.read(receipt.getRelativePath()).getInputStream()) {
                    var document = documentService.createFromInputStream(
                            application.getTenantId(),
                            application.getId(),
                            "mpesa-statement",
                            actor,
                            receipt.getOriginalFilename(),
                            receipt.getContentType(),
                            receipt.getFileSize(),
                            receipt.getStoredFilename(),
                            inputStream,
                            "inbound-statements");
                    receipt.setTenantId(application.getTenantId());
                    receipt.setMatchedApplicationId(application.getId());
                    receipt.setMatchedDocumentId(document.id());
                    receipt.setReviewNotes(notes);
                    receipt.setFailureReason(null);
                    receipt.setBackgroundError(null);
                    receipt.setMatchStatus(status);
                    receipt.setProcessedAt(Instant.now());
                    log.info(
                            "Inbound statement matched applicationId={} and documentId={} - statement analysis will trigger after commit",
                            application.getId(),
                            document.id());
                }
            } catch (Exception ex) {
                throw new IllegalStateException("Failed to attach inbound statement to application", ex);
            }
        }
    }

    private boolean phoneMatches(String phoneToken, String applicationPhoneNumber) {
        String normalizedToken = normalizePhoneToken(phoneToken);
        String normalizedPhone = normalizeDigits(applicationPhoneNumber);
        if (normalizedToken.indexOf('x') < 0) {
            return normalizedPhone.equals(normalizedToken);
        }
        int firstMaskIndex = normalizedToken.indexOf('x');
        int lastMaskIndex = normalizedToken.lastIndexOf('x');
        String prefix = normalizedToken.substring(0, firstMaskIndex);
        String suffix = normalizedToken.substring(lastMaskIndex + 1);
        boolean prefixMatches = prefix.isBlank() || normalizedPhone.startsWith(prefix);
        boolean suffixMatches = suffix.isBlank() || normalizedPhone.endsWith(suffix);
        return prefixMatches && suffixMatches;
    }

    private String normalizePhoneToken(String phoneToken) {
        return phoneToken == null ? "" : phoneToken.trim().toLowerCase().replaceAll("[^0-9x]", "");
    }

    private String normalizeDigits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    private String maskPhoneToken(String phoneToken) {
        String token = normalizePhoneToken(phoneToken);
        if (token.length() <= 4) {
            return "****";
        }
        return token.substring(0, Math.min(4, token.length())) + "***" + token.substring(Math.max(0, token.length() - 3));
    }
}
