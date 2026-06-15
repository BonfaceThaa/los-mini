package com.credvenn.lm.payment;

import com.credvenn.lm.application.LoanRequestApplication;
import com.credvenn.lm.application.LoanRequestApplicationRepository;
import com.credvenn.lm.common.api.PagedResponse;
import com.credvenn.lm.common.api.PaginationSupport;
import com.credvenn.lm.common.exception.NotFoundException;
import com.credvenn.lm.common.logging.LoggingContext;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DepositPaymentService {

    private static final Logger log = LoggerFactory.getLogger(DepositPaymentService.class);
    private static final DateTimeFormatter TRANSACTION_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ZoneId MPESA_ZONE = ZoneId.of("Africa/Nairobi");
    private static final Map<String, String> DEPOSIT_SORTS = new LinkedHashMap<>();

    static {
        DEPOSIT_SORTS.put("transactionTime", "transactionTime");
        DEPOSIT_SORTS.put("transactionAmount", "transactionAmount");
        DEPOSIT_SORTS.put("billRefNumber", "billRefNumber");
        DEPOSIT_SORTS.put("businessShortCode", "businessShortCode");
        DEPOSIT_SORTS.put("status", "status");
        DEPOSIT_SORTS.put("createdAt", "createdAt");
        DEPOSIT_SORTS.put("updatedAt", "updatedAt");
    }

    private final DepositPaymentRepository depositPaymentRepository;
    private final TenantPaymentChannelRepository channelRepository;
    private final LoanRequestApplicationRepository applicationRepository;

    public DepositPaymentService(
            DepositPaymentRepository depositPaymentRepository,
            TenantPaymentChannelRepository channelRepository,
            LoanRequestApplicationRepository applicationRepository) {
        this.depositPaymentRepository = depositPaymentRepository;
        this.channelRepository = channelRepository;
        this.applicationRepository = applicationRepository;
    }

    @Transactional
    public DepositPayment acceptDepositCallback(
            String tenantId,
            String transactionType,
            String transId,
            String transTime,
            java.math.BigDecimal transAmount,
            String businessShortCode,
            String billRefNumber,
            String msisdn,
            String firstName,
            String middleName,
            String lastName,
            String rawPayload) {
        return acceptDepositCallbackInternal(
                tenantId,
                transactionType,
                transId,
                transTime,
                transAmount,
                businessShortCode,
                billRefNumber,
                msisdn,
                firstName,
                middleName,
                lastName,
                rawPayload);
    }

    @Transactional
    public DepositPayment acceptDepositCallback(
            String transactionType,
            String transId,
            String transTime,
            java.math.BigDecimal transAmount,
            String businessShortCode,
            String billRefNumber,
            String msisdn,
            String firstName,
            String middleName,
            String lastName,
            String rawPayload) {
        return acceptDepositCallbackInternal(
                null,
                transactionType,
                transId,
                transTime,
                transAmount,
                businessShortCode,
                billRefNumber,
                msisdn,
                firstName,
                middleName,
                lastName,
                rawPayload);
    }

    private DepositPayment acceptDepositCallbackInternal(
            String tenantId,
            String transactionType,
            String transId,
            String transTime,
            java.math.BigDecimal transAmount,
            String businessShortCode,
            String billRefNumber,
            String msisdn,
            String firstName,
            String middleName,
            String lastName,
            String rawPayload) {
        String receiptNumber = transId.trim();
        if (depositPaymentRepository.existsByMpesaReceiptNumber(receiptNumber)) {
            log.info("Ignoring duplicate deposit callback for receiptNumber={}", receiptNumber);
            return null;
        }

        TenantPaymentChannel channel = resolveChannel(tenantId, businessShortCode);
        String resolvedTenantId = channel.getTenantId();
        String normalizedPhoneNumber = normalizePhone(billRefNumber);
        LoanRequestApplication application = findMatchingApplication(resolvedTenantId, normalizedPhoneNumber);

        DepositPayment depositPayment = new DepositPayment();
        depositPayment.setTenantId(resolvedTenantId);
        depositPayment.setBusinessShortCode(businessShortCode.trim());
        depositPayment.setBillRefNumber(billRefNumber.trim());
        depositPayment.setNormalizedPhoneNumber(normalizedPhoneNumber);
        depositPayment.setTransactionAmount(transAmount);
        depositPayment.setTransactionTime(parseTransactionTime(transTime));
        depositPayment.setMpesaReceiptNumber(receiptNumber);
        depositPayment.setMsisdn(msisdn);
        depositPayment.setPayerFirstName(firstName);
        depositPayment.setPayerMiddleName(middleName);
        depositPayment.setPayerLastName(lastName);
        depositPayment.setRawPayload(rawPayload);
        depositPayment.setStatus(DepositPaymentStatus.RECEIVED);

        if (application != null) {
            depositPayment.setMatchedApplicationId(application.getId());
            depositPayment.setMatchedFineractClientId(application.getFineractClientId());
            depositPayment.setStatus(DepositPaymentStatus.MATCHED);
            log.info(
                    "Matched deposit callback tenantId={} applicationId={} receiptNumber={} phone={}",
                    resolvedTenantId,
                    application.getId(),
                    receiptNumber,
                    LoggingContext.maskPhone(normalizedPhoneNumber));
        } else {
            depositPayment.setStatus(DepositPaymentStatus.UNMATCHED);
            depositPayment.setFailureReason("No tenant application matched the bill reference phone number");
            log.warn(
                    "Unmatched deposit callback tenantId={} receiptNumber={} phone={}",
                    resolvedTenantId,
                    receiptNumber,
                    LoggingContext.maskPhone(normalizedPhoneNumber));
        }

        return depositPaymentRepository.save(depositPayment);
    }

    @Transactional(readOnly = true)
    public PagedResponse<PaymentDtos.DepositPaymentResponse> listTenantDeposits(
            String tenantId,
            Integer page,
            Integer size,
            String sortBy,
            String sortDir) {
        Pageable pageable = PaginationSupport.pageable(page, size, sortBy, sortDir, DEPOSIT_SORTS, "transactionTime");
        String normalizedSortBy = PaginationSupport.normalizeSortBy(sortBy, DEPOSIT_SORTS, "transactionTime");
        String normalizedSortDir = PaginationSupport.normalizeDirectionValue(sortDir);
        var resultPage = depositPaymentRepository.findAllByTenantId(tenantId, pageable)
                .map(PaymentDtos.DepositPaymentResponse::from);
        return PagedResponse.fromPage(resultPage, normalizedSortBy, normalizedSortDir);
    }

    @Transactional(readOnly = true)
    public DepositPayment getTenantDeposit(String tenantId, String depositId) {
        return depositPaymentRepository.findByIdAndTenantId(depositId, tenantId)
                .orElseThrow(() -> new NotFoundException("Deposit payment not found"));
    }

    @Transactional(readOnly = true)
    public PagedResponse<PaymentDtos.DepositPaymentResponse> listTenantDepositsByApplication(
            String tenantId,
            String applicationId,
            Integer page,
            Integer size,
            String sortBy,
            String sortDir) {
        Pageable pageable = PaginationSupport.pageable(page, size, sortBy, sortDir, DEPOSIT_SORTS, "transactionTime");
        String normalizedSortBy = PaginationSupport.normalizeSortBy(sortBy, DEPOSIT_SORTS, "transactionTime");
        String normalizedSortDir = PaginationSupport.normalizeDirectionValue(sortDir);
        var resultPage = depositPaymentRepository.findAllByTenantIdAndMatchedApplicationId(tenantId, applicationId, pageable)
                .map(PaymentDtos.DepositPaymentResponse::from);
        return PagedResponse.fromPage(resultPage, normalizedSortBy, normalizedSortDir);
    }

    @Transactional(readOnly = true)
    public PagedResponse<PaymentDtos.DepositPaymentResponse> listTenantDepositsByClient(
            String tenantId,
            String fineractClientId,
            Integer page,
            Integer size,
            String sortBy,
            String sortDir) {
        Pageable pageable = PaginationSupport.pageable(page, size, sortBy, sortDir, DEPOSIT_SORTS, "transactionTime");
        String normalizedSortBy = PaginationSupport.normalizeSortBy(sortBy, DEPOSIT_SORTS, "transactionTime");
        String normalizedSortDir = PaginationSupport.normalizeDirectionValue(sortDir);
        var resultPage = depositPaymentRepository.findAllByTenantIdAndMatchedFineractClientId(tenantId, fineractClientId, pageable)
                .map(PaymentDtos.DepositPaymentResponse::from);
        return PagedResponse.fromPage(resultPage, normalizedSortBy, normalizedSortDir);
    }

    private TenantPaymentChannel resolveChannel(String tenantId, String businessShortCode) {
        String normalizedShortCode = businessShortCode.trim();
        if (tenantId != null && !tenantId.isBlank()) {
            return channelRepository.findByTenantIdAndShortCodeAndActiveTrue(tenantId, normalizedShortCode)
                    .orElseThrow(() -> new NotFoundException("No active tenant payment channel matched the business short code"));
        }
        return channelRepository.findByShortCodeAndActiveTrue(normalizedShortCode)
                .orElseThrow(() -> new NotFoundException("No tenant payment channel matched the business short code"));
    }

    private LoanRequestApplication findMatchingApplication(String tenantId, String normalizedPhoneNumber) {
        if (normalizedPhoneNumber == null || normalizedPhoneNumber.isBlank()) {
            return null;
        }
        return applicationRepository.findAllByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .filter(application -> normalizedPhoneNumber.equals(normalizePhone(application.getPhoneNumber())))
                .findFirst()
                .orElse(null);
    }

    private Instant parseTransactionTime(String value) {
        return LocalDateTime.parse(value.trim(), TRANSACTION_TIME_FORMAT).atZone(MPESA_ZONE).toInstant();
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
}
