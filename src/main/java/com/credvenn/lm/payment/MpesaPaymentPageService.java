package com.credvenn.lm.payment;

import com.credvenn.lm.application.ApplicationStatus;
import com.credvenn.lm.application.LoanRequestApplication;
import com.credvenn.lm.application.LoanRequestApplicationRepository;
import com.credvenn.lm.common.exception.ForbiddenOperationException;
import com.credvenn.lm.common.logging.LoggingContext;
import com.credvenn.lm.security.AuthenticatedService;
import com.credvenn.lm.tenant.TenantBrandingDtos;
import com.credvenn.lm.tenant.TenantBrandingService;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MpesaPaymentPageService {

    private static final Logger log = LoggerFactory.getLogger(MpesaPaymentPageService.class);
    private static final ApplicationStatus ELIGIBLE_STK_STATUS = ApplicationStatus.FINERACT_LOAN_ACTIVATED;

    private final TenantBrandingService tenantBrandingService;
    private final LoanRequestApplicationRepository applicationRepository;
    private final TenantPaymentChannelRepository paymentChannelRepository;
    private final MpesaStkPushRequestRepository stkPushRequestRepository;
    private final MpesaStkPushGateway stkPushGateway;
    private final TenantPaymentChannelService tenantPaymentChannelService;

    public MpesaPaymentPageService(
            TenantBrandingService tenantBrandingService,
            LoanRequestApplicationRepository applicationRepository,
            TenantPaymentChannelRepository paymentChannelRepository,
            MpesaStkPushRequestRepository stkPushRequestRepository,
            MpesaStkPushGateway stkPushGateway,
            TenantPaymentChannelService tenantPaymentChannelService) {
        this.tenantBrandingService = tenantBrandingService;
        this.applicationRepository = applicationRepository;
        this.paymentChannelRepository = paymentChannelRepository;
        this.stkPushRequestRepository = stkPushRequestRepository;
        this.stkPushGateway = stkPushGateway;
        this.tenantPaymentChannelService = tenantPaymentChannelService;
    }

    @Transactional(readOnly = true)
    public TenantBrandingDtos.TenantBrandingResponse getBranding(AuthenticatedService actor) {
        String tenantId = requireTenantId(actor);
        return tenantBrandingService.getBranding(tenantId);
    }

    @Transactional
    public void initiateStkPush(
            AuthenticatedService actor,
            String phoneNumber) {
        String tenantId = requireTenantId(actor);
        String normalizedPhoneNumber = normalizePhoneNumber(phoneNumber);

        MpesaStkPushRequest stkRequest = new MpesaStkPushRequest();
        stkRequest.setTenantId(tenantId);
        stkRequest.setRequestedPhoneNumber(phoneNumber.trim());
        stkRequest.setNormalizedPhoneNumber(normalizedPhoneNumber);
        stkRequest.setServiceName(actor.serviceName());
        stkRequest.setStatus(MpesaStkPushRequestStatus.RECEIVED);

        try {
            LoanRequestApplication application = resolveOldestLoan(tenantId, normalizedPhoneNumber);
            TenantPaymentChannel channel = paymentChannelRepository
                    .findFirstByTenantIdAndChannelTypeAndActiveTrueOrderByCreatedAtAsc(tenantId, PaymentChannelType.MPESA_PAYBILL)
                    .orElseThrow(() -> new IllegalStateException("No active M-PESA paybill channel configured for tenant"));
            TenantMpesaIntegrationConfig config = tenantPaymentChannelService.getRequiredIntegrationConfig(channel);

            String fineractLoanId = application.getFineractLoanId();
            String billReference = "LN-" + fineractLoanId;
            stkRequest.setApplicationId(application.getId());
            stkRequest.setFineractLoanId(fineractLoanId);
            stkRequest.setInstallmentAmount(application.getInstallmentAmount());
            stkRequest.setBusinessShortCode(config.businessShortCode());
            stkRequest.setBillRefNumber(billReference);

            MpesaStkPushGateway.InitiationResult result = stkPushGateway.initiate(new MpesaStkPushGateway.InitiationCommand(
                    config,
                    billReference,
                    normalizedPhoneNumber,
                    application.getInstallmentAmount(),
                    "Installment payment for loan " + fineractLoanId));

            stkRequest.setStatus(MpesaStkPushRequestStatus.ACCEPTED);
            stkRequest.setInitiatedAt(Instant.now());
            stkRequest.setMerchantRequestId(result.merchantRequestId());
            stkRequest.setCheckoutRequestId(result.checkoutRequestId());
            stkRequest.setProviderResponseCode(result.responseCode());
            stkRequest.setProviderResponseDescription(result.responseDescription());
            stkRequest.setProviderCustomerMessage(result.customerMessage());
            stkRequest.setRawProviderResponse(result.rawResponse());

            log.info(
                    "Accepted STK push request tenantId={} applicationId={} loanId={} phone={} service={}",
                    tenantId,
                    application.getId(),
                    fineractLoanId,
                    LoggingContext.maskPhone(normalizedPhoneNumber),
                    actor.serviceName());
        } catch (Exception ex) {
            stkRequest.setStatus(MpesaStkPushRequestStatus.FAILED);
            stkRequest.setFailureReason(ex.getMessage());
            log.warn(
                    "Failed to initiate STK push tenantId={} phone={} service={} reason={}",
                    tenantId,
                    LoggingContext.maskPhone(normalizedPhoneNumber),
                    actor.serviceName(),
                    ex.getMessage());
        }

        stkPushRequestRepository.save(stkRequest);
    }

    private LoanRequestApplication resolveOldestLoan(String tenantId, String normalizedPhoneNumber) {
        List<LoanRequestApplication> matches = applicationRepository
                .findAllByTenantIdAndFineractLoanIdIsNotNullAndInstallmentAmountIsNotNullOrderByCreatedAtAsc(tenantId)
                .stream()
                .filter(application -> ELIGIBLE_STK_STATUS == application.getStatus())
                .filter(application -> normalizedPhoneNumber.equals(normalizePhoneNumber(application.getPhoneNumber())))
                .toList();
        if (matches.isEmpty()) {
            throw new IllegalStateException("No payable loan matched the supplied phone number");
        }
        return matches.getFirst();
    }

    private String requireTenantId(AuthenticatedService actor) {
        if (actor.tenantId() == null || actor.tenantId().isBlank()) {
            throw new ForbiddenOperationException("Service token is not bound to a tenant");
        }
        return actor.tenantId();
    }

    private String normalizePhoneNumber(String phoneNumber) {
        String digits = phoneNumber == null ? "" : phoneNumber.replaceAll("[^0-9]", "");
        if (digits.startsWith("0") && digits.length() == 10) {
            digits = "254" + digits.substring(1);
        } else if (digits.startsWith("7") && digits.length() == 9) {
            digits = "254" + digits;
        }
        if (!digits.matches("^2547\\d{8}$")) {
            throw new IllegalStateException("Phone number must normalize to 2547XXXXXXXX");
        }
        return digits;
    }
}
