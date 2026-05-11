package com.credvenn.lm.payment;

import com.credvenn.lm.common.exception.NotFoundException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
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
    private final ApplicationEventPublisher eventPublisher;

    public MpesaPaymentService(
            MpesaPaymentReceiptRepository receiptRepository,
            ApplicationEventPublisher eventPublisher) {
        this.receiptRepository = receiptRepository;
        this.eventPublisher = eventPublisher;
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
}
