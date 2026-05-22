package com.credvenn.lm.payment;

import com.credvenn.lm.application.ApplicationStatus;
import com.credvenn.lm.application.LoanRequestApplication;
import com.credvenn.lm.application.LoanRequestApplicationRepository;
import com.credvenn.lm.common.exception.NotFoundException;
import com.credvenn.lm.common.logging.LoggingContext;
import com.credvenn.lm.devicecontrol.LoanRepaymentPostedEvent;
import com.credvenn.lm.fineract.FineractGateway;
import com.credvenn.lm.fineract.FineractGateway.LoanSummary;
import com.credvenn.lm.fineract.FineractGateway.LoanRepaymentRequest;
import com.credvenn.lm.tenant.Tenant;
import com.credvenn.lm.tenant.TenantService;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MpesaPaymentProcessor {

    private static final Logger log = LoggerFactory.getLogger(MpesaPaymentProcessor.class);
    private static final ZoneId REPAYMENT_ZONE = ZoneId.of("Africa/Nairobi");
    private static final DateTimeFormatter REPAYMENT_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.ENGLISH);

    private final MpesaPaymentReceiptRepository receiptRepository;
    private final TenantPaymentChannelRepository channelRepository;
    private final LoanRequestApplicationRepository applicationRepository;
    private final TenantService tenantService;
    private final FineractGateway fineractGateway;
    private final ApplicationEventPublisher eventPublisher;

    public MpesaPaymentProcessor(
            MpesaPaymentReceiptRepository receiptRepository,
            TenantPaymentChannelRepository channelRepository,
            LoanRequestApplicationRepository applicationRepository,
            TenantService tenantService,
            FineractGateway fineractGateway,
            ApplicationEventPublisher eventPublisher) {
        this.receiptRepository = receiptRepository;
        this.channelRepository = channelRepository;
        this.applicationRepository = applicationRepository;
        this.tenantService = tenantService;
        this.fineractGateway = fineractGateway;
        this.eventPublisher = eventPublisher;
    }

    @Async
    @Transactional
    public void process(String receiptId) {
        MpesaPaymentReceipt receipt = receiptRepository.findById(receiptId)
                .orElseThrow(() -> new NotFoundException("Mpesa payment receipt not found"));
        try (LoggingContext.Scope ignored = LoggingContext.withApplication(receiptId)) {
            receipt.setProcessingStatus(MpesaPaymentProcessingStatus.PROCESSING);
            receipt.setProcessingStartedAt(Instant.now());
            log.info("Processing Mpesa payment receiptNumber={} shortCode={}", receipt.getMpesaReceiptNumber(), receipt.getBusinessShortCode());

            TenantPaymentChannel channel = channelRepository.findByShortCodeAndActiveTrue(receipt.getBusinessShortCode())
                    .orElseThrow(() -> new NotFoundException("No tenant payment channel matched the business short code"));
            Tenant tenant = tenantService.getRequiredTenant(channel.getTenantId());
            receipt.setTenantId(tenant.getId());

            LoanRequestApplication application = null;
            String normalizedPhone = receipt.getNormalizedPhoneNumber();
            if (receipt.getMatchedApplicationId() != null && !receipt.getMatchedApplicationId().isBlank()) {
                application = applicationRepository.findByIdAndTenantId(receipt.getMatchedApplicationId(), tenant.getId()).orElse(null);
                if (application != null && (normalizedPhone == null || normalizedPhone.isBlank())) {
                    normalizedPhone = normalizePhone(application.getPhoneNumber());
                }
            }
            if (normalizedPhone == null || normalizedPhone.isBlank()) {
                normalizedPhone = normalizeBillRefPhone(receipt.getBillRefNumber());
            }
            receipt.setNormalizedPhoneNumber(normalizedPhone);

            if (application == null) {
                List<LoanRequestApplication> candidates = applicationRepository.findAllByTenantIdAndStatusAndFineractLoanIdIsNotNullOrderByCreatedAtDesc(
                        tenant.getId(),
                        ApplicationStatus.FINERACT_LOAN_ACTIVATED);
                for (LoanRequestApplication candidate : candidates) {
                    if (normalizedPhone.equals(normalizePhone(candidate.getPhoneNumber()))) {
                        application = candidate;
                        break;
                    }
                }
            }

            if (application == null) {
                receipt.setProcessingStatus(MpesaPaymentProcessingStatus.LOAN_NOT_FOUND);
                receipt.setFailureReason("No activated loan request application matched the bill reference phone number");
                receipt.setProcessedAt(Instant.now());
                log.warn("No local activated loan matched normalizedPhone={}", normalizedPhone);
                return;
            }

            receipt.setMatchedApplicationId(application.getId());
            receipt.setMatchedFineractClientId(application.getFineractClientId());
            receipt.setMatchedFineractLoanId(application.getFineractLoanId());
            receipt.setProcessingStatus(MpesaPaymentProcessingStatus.MATCHED);

            LoanSummary loanSummary = fineractGateway.getLoanSummary(tenant, application.getFineractLoanId());
            if (!loanSummary.active()) {
                receipt.setProcessingStatus(MpesaPaymentProcessingStatus.LOAN_NOT_FOUND);
                receipt.setFailureReason("Matched Fineract loan is not active");
                receipt.setProcessedAt(Instant.now());
                log.warn("Matched Fineract loan is not active fineractLoanId={}", application.getFineractLoanId());
                return;
            }

            String transactionId = fineractGateway.postLoanRepayment(
                    tenant,
                    application.getFineractLoanId(),
                    buildLoanRepaymentRequest(application.getFineractLoanId(), receipt));
            receipt.setFineractTransactionId(transactionId);
            receipt.setProcessingStatus(MpesaPaymentProcessingStatus.REPAYMENT_POSTED);
            receipt.setProcessedAt(Instant.now());
            receipt.setFailureReason(null);
            log.info("Posted Fineract repayment transactionId={} for fineractLoanId={}", transactionId, application.getFineractLoanId());
            eventPublisher.publishEvent(new LoanRepaymentPostedEvent(
                    tenant.getId(),
                    application.getId(),
                    application.getFineractLoanId(),
                    receipt.getId()));
        } catch (Exception ex) {
            receipt.setProcessingStatus(MpesaPaymentProcessingStatus.FAILED);
            receipt.setFailureReason(ex.getMessage());
            receipt.setProcessedAt(Instant.now());
            log.error("Mpesa payment processing failed", ex);
        }
    }

    private String normalizeBillRefPhone(String billRefNumber) {
        String digits = billRefNumber == null ? "" : billRefNumber.replaceAll("\\D", "");
        if (digits.startsWith("0") && digits.length() == 10) {
            return "254" + digits.substring(1);
        }
        if (digits.startsWith("254") && digits.length() >= 12) {
            return digits;
        }
        return digits;
    }

    private String normalizePhone(String phone) {
        String digits = phone == null ? "" : phone.replaceAll("\\D", "");
        if (digits.startsWith("0") && digits.length() == 10) {
            return "254" + digits.substring(1);
        }
        if (digits.startsWith("254")) {
            return digits;
        }
        return digits;
    }

    private LoanRepaymentRequest buildLoanRepaymentRequest(String fineractLoanId, MpesaPaymentReceipt receipt) {
        String receiptNumber = safeText(receipt.getMpesaReceiptNumber());
        String msisdn = safeText(receipt.getMsisdn());
        return new LoanRepaymentRequest(
                receipt.getTransactionTime().atZone(REPAYMENT_ZONE).toLocalDate().format(REPAYMENT_DATE_FORMATTER),
                receipt.getTransactionAmount().setScale(2, RoundingMode.HALF_UP).toPlainString(),
                "1",
                "M-Pesa receipt %s from %s".formatted(receiptNumber, msisdn),
                formatAccountNumber(fineractLoanId),
                receiptNumber,
                safeText(receipt.getBusinessShortCode()),
                receiptNumber,
                msisdn);
    }

    private String formatAccountNumber(String fineractLoanId) {
        String digits = fineractLoanId == null ? "" : fineractLoanId.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return "000000000";
        }
        if (digits.length() >= 9) {
            return digits;
        }
        return "0".repeat(9 - digits.length()) + digits;
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }
}
