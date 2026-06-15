package com.credvenn.lm.payment;

import com.credvenn.lm.application.LoanRequestApplicationRepository;
import com.credvenn.lm.common.exception.NotFoundException;
import com.credvenn.lm.common.logging.LoggingContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@Service
public class MpesaPaymentService {

    private static final DateTimeFormatter TRANSACTION_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ZoneId MPESA_ZONE = ZoneId.of("Africa/Nairobi");
    private static final Logger log = LoggerFactory.getLogger(MpesaPaymentService.class);

    private final MpesaPaymentReceiptRepository receiptRepository;
    private final MpesaStkPushRequestRepository stkPushRequestRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final LoanRequestApplicationRepository applicationRepository;

    public MpesaPaymentService(
            MpesaPaymentReceiptRepository receiptRepository,
            MpesaStkPushRequestRepository stkPushRequestRepository,
            ApplicationEventPublisher eventPublisher,
            LoanRequestApplicationRepository applicationRepository) {
        this.receiptRepository = receiptRepository;
        this.stkPushRequestRepository = stkPushRequestRepository;
        this.eventPublisher = eventPublisher;
        this.applicationRepository = applicationRepository;
    }

    @Transactional
    public void acceptDarajaCallback(
            String transId,
            String transTime,
            BigDecimal transAmount,
            String businessShortCode,
            String billRefNumber,
            String msisdn,
            String firstName,
            String middleName,
            String lastName,
            String rawPayload) {
        String receiptNumber = transId.trim();
        if (!receiptRepository.existsByMpesaReceiptNumber(receiptNumber)) {
            MpesaPaymentReceipt receipt = new MpesaPaymentReceipt();
            receipt.setBusinessShortCode(businessShortCode.trim());
            receipt.setBillRefNumber(billRefNumber.trim());
            receipt.setTransactionAmount(transAmount);
            receipt.setTransactionTime(parseTransactionTime(transTime));
            receipt.setMpesaReceiptNumber(receiptNumber);
            receipt.setMsisdn(msisdn);
            receipt.setPayerFirstName(firstName);
            receipt.setPayerMiddleName(middleName);
            receipt.setPayerLastName(lastName);
            receipt.setProcessingStatus(MpesaPaymentProcessingStatus.RECEIVED);
            receipt.setRawPayload(rawPayload);
            receipt = receiptRepository.save(receipt);
            log.info(
                    "Stored Mpesa receipt id={} receiptNumber={} shortCode={} amount={} and publishing processing event",
                    receipt.getId(),
                    receipt.getMpesaReceiptNumber(),
                    receipt.getBusinessShortCode(),
                    receipt.getTransactionAmount());
            eventPublisher.publishEvent(new MpesaPaymentAcceptedEvent(receipt.getId()));
        } else {
            log.info("Ignoring duplicate Mpesa callback for receiptNumber={}", receiptNumber);
        }
    }

    @Transactional
    public void acceptStkCallback(
            String tenantId,
            String checkoutRequestId,
            Integer resultCode,
            String resultDesc,
            List<Map.Entry<String, Object>> metadataItems,
            String rawProviderResponse) {
        MpesaStkPushRequest stkRequest = stkPushRequestRepository.findByCheckoutRequestId(checkoutRequestId)
                .orElseThrow(() -> new NotFoundException("STK push request not found"));
        if (!tenantId.equals(stkRequest.getTenantId())) {
            throw new NotFoundException("STK push request not found");
        }

        stkRequest.setProviderResponseCode(String.valueOf(resultCode));
        stkRequest.setProviderResponseDescription(resultDesc);
        stkRequest.setRawProviderResponse(rawProviderResponse);

        if (resultCode == null || resultCode != 0) {
            stkRequest.setStatus(MpesaStkPushRequestStatus.FAILED);
            stkRequest.setFailureReason(resultDesc);
            return;
        }

        Map<String, Object> metadata = toMetadataMap(metadataItems);
        String receiptNumber = text(metadata.get("MpesaReceiptNumber"));
        if (receiptNumber == null || receiptNumber.isBlank()) {
            stkRequest.setStatus(MpesaStkPushRequestStatus.FAILED);
            stkRequest.setFailureReason("Successful STK callback did not include MpesaReceiptNumber");
            return;
        }
        if (receiptRepository.findByMatchedApplicationIdAndMpesaReceiptNumber(stkRequest.getApplicationId(), receiptNumber).isPresent()) {
            return;
        }

        MpesaPaymentReceipt receipt = new MpesaPaymentReceipt();
        receipt.setTenantId(stkRequest.getTenantId());
        receipt.setBusinessShortCode(stkRequest.getBusinessShortCode());
        receipt.setBillRefNumber(stkRequest.getBillRefNumber());
        receipt.setNormalizedPhoneNumber(stkRequest.getNormalizedPhoneNumber());
        receipt.setTransactionAmount(decimal(metadata.get("Amount"), stkRequest.getInstallmentAmount()));
        receipt.setTransactionTime(parseCallbackTransactionTime(text(metadata.get("TransactionDate"))));
        receipt.setMpesaReceiptNumber(receiptNumber);
        receipt.setMsisdn(text(metadata.get("PhoneNumber")));
        receipt.setProcessingStatus(MpesaPaymentProcessingStatus.RECEIVED);
        receipt.setMatchedApplicationId(stkRequest.getApplicationId());
        receipt.setMatchedFineractLoanId(stkRequest.getFineractLoanId());
        var matchedApplication = applicationRepository.findByIdAndTenantId(stkRequest.getApplicationId(), stkRequest.getTenantId());
        if (matchedApplication.isPresent()) {
            receipt.setMatchedFineractClientId(matchedApplication.get().getFineractClientId());
        }
        receipt.setRawPayload(rawProviderResponse);
        receipt = receiptRepository.save(receipt);
        stkRequest.setStatus(MpesaStkPushRequestStatus.ACCEPTED);
        eventPublisher.publishEvent(new MpesaPaymentAcceptedEvent(receipt.getId()));
        log.info(
                "Accepted tenant STK callback tenantId={} applicationId={} loanId={} phone={} receiptNumber={}",
                stkRequest.getTenantId(),
                stkRequest.getApplicationId(),
                stkRequest.getFineractLoanId(),
                LoggingContext.maskPhone(stkRequest.getNormalizedPhoneNumber()),
                receiptNumber);
    }

    @Transactional(readOnly = true)
    public List<MpesaPaymentReceipt> listReceipts() {
        return receiptRepository.findAllByProcessingStatusInOrderByCreatedAtDesc(Set.of(
                        MpesaPaymentProcessingStatus.RECEIVED,
                        MpesaPaymentProcessingStatus.PROCESSING,
                        MpesaPaymentProcessingStatus.LOAN_NOT_FOUND,
                        MpesaPaymentProcessingStatus.REPAYMENT_FAILED,
                        MpesaPaymentProcessingStatus.FAILED));
    }

    @Transactional(readOnly = true)
    public MpesaPaymentReceipt getReceipt(String receiptId) {
        return receiptRepository.findById(receiptId)
                .orElseThrow(() -> new NotFoundException("Mpesa payment receipt not found"));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MpesaPaymentReceipt retryReceipt(String receiptId) {
        MpesaPaymentReceipt receipt = receiptRepository.findById(receiptId)
                .orElseThrow(() -> new NotFoundException("Mpesa payment receipt not found"));
        if (receipt.getProcessingStatus() == MpesaPaymentProcessingStatus.REPAYMENT_POSTED) {
            return receipt;
        }
        receipt.setProcessingStatus(MpesaPaymentProcessingStatus.RECEIVED);
        receipt.setFailureReason(null);
        receipt.setProcessingStartedAt(null);
        receipt.setProcessedAt(null);
        eventPublisher.publishEvent(new MpesaPaymentAcceptedEvent(receipt.getId()));
        return receipt;
    }

    private Instant parseTransactionTime(String value) {
        return LocalDateTime.parse(value.trim(), TRANSACTION_TIME_FORMAT).atZone(MPESA_ZONE).toInstant();
    }

    private Instant parseCallbackTransactionTime(String value) {
        if (value == null || value.isBlank()) {
            return Instant.now();
        }
        return parseTransactionTime(value);
    }

    private Map<String, Object> toMetadataMap(List<Map.Entry<String, Object>> items) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (items == null) {
            return metadata;
        }
        for (Map.Entry<String, Object> item : items) {
            if (item != null && item.getKey() != null) {
                metadata.put(item.getKey(), item.getValue());
            }
        }
        return metadata;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private BigDecimal decimal(Object value, BigDecimal fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
