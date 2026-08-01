package com.credvenn.lm.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.credvenn.lm.application.ApplicationStatus;
import com.credvenn.lm.application.LoanRequestApplication;
import com.credvenn.lm.application.LoanRequestApplicationRepository;
import com.credvenn.lm.security.AuthenticatedService;
import com.credvenn.lm.tenant.TenantBrandingService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MpesaPaymentPageServiceTest {

    @Test
    void initiateStkPushSkipsClosedLoansAndUsesActiveLoan() {
        TestContext context = new TestContext();
        LoanRequestApplication closed = application("app-closed", "tenant-1", "254700000000", "loan-closed", ApplicationStatus.LOAN_CLOSED);
        LoanRequestApplication active = application("app-active", "tenant-1", "254700000000", "loan-active", ApplicationStatus.FINERACT_LOAN_ACTIVATED);
        when(context.applicationRepository.findAllByTenantIdAndFineractLoanIdIsNotNullAndInstallmentAmountIsNotNullOrderByCreatedAtAsc("tenant-1"))
                .thenReturn(List.of(closed, active));
        when(context.paymentChannelRepository.findFirstByTenantIdAndChannelTypeAndActiveTrueOrderByCreatedAtAsc("tenant-1", PaymentChannelType.MPESA_PAYBILL))
                .thenReturn(Optional.of(channel("tenant-1", "600111")));
        when(context.tenantPaymentChannelService.getRequiredIntegrationConfig(any(TenantPaymentChannel.class)))
                .thenReturn(new TenantMpesaIntegrationConfig(
                        DarajaEnvironment.SANDBOX,
                        "600111",
                        "http://callback",
                        "enc-key",
                        "enc-secret",
                        "enc-passkey",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null));
        when(context.stkPushGateway.initiate(any()))
                .thenReturn(new MpesaStkPushGateway.InitiationResult(
                        "merchant-1",
                        "checkout-1",
                        "0",
                        "Accepted",
                        "Success",
                        "{}"));

        context.service.initiateStkPush(
                new AuthenticatedService("repayment-page", "tenant-1", List.of(), List.of()),
                "0700000000");

        verify(context.stkPushGateway).initiate(any());
        verify(context.stkPushRequestRepository).save(any(MpesaStkPushRequest.class));
    }

    @Test
    void initiateStkPushFailsWhenOnlyClosedLoanMatchesPhone() {
        TestContext context = new TestContext();
        LoanRequestApplication closed = application("app-closed", "tenant-1", "254700000000", "loan-closed", ApplicationStatus.LOAN_CLOSED);
        when(context.applicationRepository.findAllByTenantIdAndFineractLoanIdIsNotNullAndInstallmentAmountIsNotNullOrderByCreatedAtAsc("tenant-1"))
                .thenReturn(List.of(closed));

        context.service.initiateStkPush(
                new AuthenticatedService("repayment-page", "tenant-1", List.of(), List.of()),
                "0700000000");

        verify(context.stkPushGateway, never()).initiate(any());
        verify(context.stkPushRequestRepository).save(any(MpesaStkPushRequest.class));
    }

    private static LoanRequestApplication application(
            String id,
            String tenantId,
            String phoneNumber,
            String fineractLoanId,
            ApplicationStatus status) {
        LoanRequestApplication application = new LoanRequestApplication();
        setId(application, id);
        application.setTenantId(tenantId);
        application.setPhoneNumber(phoneNumber);
        application.setFineractLoanId(fineractLoanId);
        application.setStatus(status);
        application.setInstallmentAmount(new BigDecimal("800.00"));
        return application;
    }

    private static TenantPaymentChannel channel(String tenantId, String shortCode) {
        TenantPaymentChannel channel = new TenantPaymentChannel();
        channel.setTenantId(tenantId);
        channel.setShortCode(shortCode);
        channel.setActive(true);
        channel.setChannelType(PaymentChannelType.MPESA_PAYBILL);
        return channel;
    }

    private static void setId(LoanRequestApplication application, String id) {
        try {
            var field = LoanRequestApplication.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(application, id);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static final class TestContext {
        private final TenantBrandingService tenantBrandingService = mock(TenantBrandingService.class);
        private final LoanRequestApplicationRepository applicationRepository = mock(LoanRequestApplicationRepository.class);
        private final TenantPaymentChannelRepository paymentChannelRepository = mock(TenantPaymentChannelRepository.class);
        private final MpesaStkPushRequestRepository stkPushRequestRepository = mock(MpesaStkPushRequestRepository.class);
        private final MpesaStkPushGateway stkPushGateway = mock(MpesaStkPushGateway.class);
        private final TenantPaymentChannelService tenantPaymentChannelService = mock(TenantPaymentChannelService.class);
        private final MpesaPaymentPageService service = new MpesaPaymentPageService(
                tenantBrandingService,
                applicationRepository,
                paymentChannelRepository,
                stkPushRequestRepository,
                stkPushGateway,
                tenantPaymentChannelService);
    }
}
