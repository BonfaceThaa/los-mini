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
    public PaymentDtos.DarajaCallbackAcknowledgement acceptDarajaCallback(PaymentDtos.DarajaCallbackRequest request) {
        String receiptNumber = request.TransID().trim();
        if (!receiptRepository.existsByMpesaReceiptNumber(receiptNumber)) {
            MpesaPaymentReceipt receipt = new MpesaPaymentReceipt();
            receipt.setBusinessShortCode(request.BusinessShortCode().trim());
            receipt.setBillRefNumber(request.BillRefNumber().trim());
            receipt.setTransactionAmount(request.TransAmount());
            receipt.setTransactionTime(parseTransactionTime(request.TransTime()));
            receipt.setMpesaReceiptNumber(receiptNumber);
            receipt.setMsisdn(request.MSISDN());
            receipt.setPayerFirstName(request.FirstName());
            receipt.setPayerMiddleName(request.MiddleName());
            receipt.setPayerLastName(request.LastName());
            receipt.setProcessingStatus(MpesaPaymentProcessingStatus.RECEIVED);
            receipt.setRawPayload(toJson(request));
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
        return new PaymentDtos.DarajaCallbackAcknowledgement(0, "Accepted");
    }

    @Transactional
    public PaymentDtos.DarajaCallbackAcknowledgement acceptStkCallback(String tenantId, PaymentDtos.DarajaStkCallbackRequest request) {
        PaymentDtos.DarajaStkCallbackRequest.StkCallback callback = request.Body().stkCallback();
        String checkoutRequestId = callback.CheckoutRequestID();
        MpesaStkPushRequest stkRequest = stkPushRequestRepository.findByCheckoutRequestId(checkoutRequestId)
                .orElseThrow(() -> new NotFoundException("STK push request not found"));
        if (!tenantId.equals(stkRequest.getTenantId())) {
            throw new NotFoundException("STK push request not found");
        }

        stkRequest.setProviderResponseCode(String.valueOf(callback.ResultCode()));
        stkRequest.setProviderResponseDescription(callback.ResultDesc());
        stkRequest.setRawProviderResponse(request.toString());

        if (callback.ResultCode() == null || callback.ResultCode() != 0) {
            stkRequest.setStatus(MpesaStkPushRequestStatus.FAILED);
            stkRequest.setFailureReason(callback.ResultDesc());
            return new PaymentDtos.DarajaCallbackAcknowledgement(0, "Accepted");
        }

        Map<String, Object> metadata = toMetadataMap(callback.CallbackMetadata());
        String receiptNumber = text(metadata.get("MpesaReceiptNumber"));
        if (receiptNumber == null || receiptNumber.isBlank()) {
            stkRequest.setStatus(MpesaStkPushRequestStatus.FAILED);
            stkRequest.setFailureReason("Successful STK callback did not include MpesaReceiptNumber");
            return new PaymentDtos.DarajaCallbackAcknowledgement(0, "Accepted");
        }
        if (receiptRepository.findByMatchedApplicationIdAndMpesaReceiptNumber(stkRequest.getApplicationId(), receiptNumber).isPresent()) {
            return new PaymentDtos.DarajaCallbackAcknowledgement(0, "Accepted");
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
        receipt.setRawPayload(request.toString());
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
        return new PaymentDtos.DarajaCallbackAcknowledgement(0, "Accepted");
    }

    @Transactional(readOnly = true)
    public List<PaymentDtos.MpesaPaymentReceiptResponse> listReceipts() {
        return receiptRepository.findAllByProcessingStatusInOrderByCreatedAtDesc(Set.of(
                        MpesaPaymentProcessingStatus.RECEIVED,
                        MpesaPaymentProcessingStatus.PROCESSING,
                        MpesaPaymentProcessingStatus.LOAN_NOT_FOUND,
                        MpesaPaymentProcessingStatus.REPAYMENT_FAILED,
                        MpesaPaymentProcessingStatus.FAILED))
                .stream()
                .map(MpesaPaymentService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PaymentDtos.MpesaPaymentReceiptResponse getReceipt(String receiptId) {
        return toResponse(receiptRepository.findById(receiptId)
                .orElseThrow(() -> new NotFoundException("Mpesa payment receipt not found")));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentDtos.RetryMpesaPaymentReceiptResponse retryReceipt(String receiptId) {
        MpesaPaymentReceipt receipt = receiptRepository.findById(receiptId)
                .orElseThrow(() -> new NotFoundException("Mpesa payment receipt not found"));
        if (receipt.getProcessingStatus() == MpesaPaymentProcessingStatus.REPAYMENT_POSTED) {
            return new PaymentDtos.RetryMpesaPaymentReceiptResponse(
                    receipt.getId(),
                    receipt.getProcessingStatus(),
                    "Repayment was already posted for this receipt.");
        }
        receipt.setProcessingStatus(MpesaPaymentProcessingStatus.RECEIVED);
        receipt.setFailureReason(null);
        receipt.setProcessingStartedAt(null);
        receipt.setProcessedAt(null);
        eventPublisher.publishEvent(new MpesaPaymentAcceptedEvent(receipt.getId()));
        return new PaymentDtos.RetryMpesaPaymentReceiptResponse(
                receipt.getId(),
                receipt.getProcessingStatus(),
                "Receipt queued for background retry.");
    }

    static PaymentDtos.MpesaPaymentReceiptResponse toResponse(MpesaPaymentReceipt receipt) {
        return new PaymentDtos.MpesaPaymentReceiptResponse(
                receipt.getId(),
                receipt.getTenantId(),
                receipt.getBusinessShortCode(),
                receipt.getBillRefNumber(),
                receipt.getNormalizedPhoneNumber(),
                receipt.getTransactionAmount(),
                receipt.getTransactionTime(),
                receipt.getMpesaReceiptNumber(),
                receipt.getMsisdn(),
                receipt.getPayerFirstName(),
                receipt.getPayerMiddleName(),
                receipt.getPayerLastName(),
                receipt.getProcessingStatus(),
                receipt.getMatchedApplicationId(),
                receipt.getMatchedFineractClientId(),
                receipt.getMatchedFineractLoanId(),
                receipt.getFineractTransactionId(),
                receipt.getFailureReason(),
                receipt.getProcessingStartedAt(),
                receipt.getProcessedAt(),
                receipt.getCreatedAt(),
                receipt.getUpdatedAt());
    }

    private Instant parseTransactionTime(String value) {
        return LocalDateTime.parse(value.trim(), TRANSACTION_TIME_FORMAT).atZone(MPESA_ZONE).toInstant();
    }

    private String toJson(PaymentDtos.DarajaCallbackRequest request) {
        return request.toString();
    }

    private Instant parseCallbackTransactionTime(String value) {
        if (value == null || value.isBlank()) {
            return Instant.now();
        }
        return parseTransactionTime(value);
    }

    private Map<String, Object> toMetadataMap(List<PaymentDtos.DarajaStkCallbackRequest.CallbackMetadataItem> items) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (items == null) {
            return metadata;
        }
        for (PaymentDtos.DarajaStkCallbackRequest.CallbackMetadataItem item : items) {
            if (item != null && item.Name() != null) {
                metadata.put(item.Name(), item.Value());
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
