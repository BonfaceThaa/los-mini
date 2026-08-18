package com.credvenn.lm.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.credvenn.lm.application.ApplicationStatus;
import com.credvenn.lm.application.LoanRequestApplication;
import com.credvenn.lm.application.LoanRequestApplicationRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DepositPaymentServiceTest {

    @Test
    void validateDepositCallbackAcceptsMatchedApplicationWithoutDeposit() {
        TestContext context = new TestContext();
        TenantPaymentChannel channel = activeChannel("tenant-1", "600111");
        LoanRequestApplication application = application("app-1", "tenant-1", "0700000000", null, null);
        when(context.channelRepository.findByTenantIdAndShortCodeAndActiveTrue("tenant-1", "600111"))
                .thenReturn(Optional.of(channel));
        when(context.applicationRepository.findAllByTenantIdOrderByCreatedAtDesc("tenant-1"))
                .thenReturn(List.of(application));
        when(context.depositPaymentRepository.existsByTenantIdAndMatchedApplicationIdAndStatusIn(
                eq("tenant-1"),
                eq("app-1"),
                any()))
                .thenReturn(false);

        DepositPaymentService.C2bValidationDecision decision =
                context.service.validateDepositCallback("tenant-1", "600111", "0700000000");

        assertTrue(decision.accepted());
        assertEquals("0", decision.resultCode());
        assertEquals("Accepted", decision.resultDesc());
        assertEquals("app-1", decision.matchedApplicationId());
    }

    @Test
    void validateDepositCallbackRejectsWhenMatchedApplicationAlreadyHasDeposit() {
        TestContext context = new TestContext();
        TenantPaymentChannel channel = activeChannel("tenant-1", "600111");
        LoanRequestApplication application = application("app-1", "tenant-1", "254700000000", null, null);
        when(context.channelRepository.findByTenantIdAndShortCodeAndActiveTrue("tenant-1", "600111"))
                .thenReturn(Optional.of(channel));
        when(context.applicationRepository.findAllByTenantIdOrderByCreatedAtDesc("tenant-1"))
                .thenReturn(List.of(application));
        when(context.depositPaymentRepository.existsByTenantIdAndMatchedApplicationIdAndStatusIn(
                eq("tenant-1"),
                eq("app-1"),
                any()))
                .thenReturn(true);

        DepositPaymentService.C2bValidationDecision decision =
                context.service.validateDepositCallback("tenant-1", "600111", "254700000000");

        assertFalse(decision.accepted());
        assertEquals("C2B00011", decision.resultCode());
        assertEquals("Rejected", decision.resultDesc());
        assertEquals("Matched application already has a deposit", decision.reason());
    }

    @Test
    void validateDepositCallbackAcceptsActivatedLoanForRepaymentRouting() {
        TestContext context = new TestContext();
        TenantPaymentChannel channel = activeChannel("tenant-1", "600111");
        LoanRequestApplication application = application(
                "app-1",
                "tenant-1",
                "254700000000",
                ApplicationStatus.FINERACT_LOAN_ACTIVATED,
                "loan-1");
        when(context.channelRepository.findByTenantIdAndShortCodeAndActiveTrue("tenant-1", "600111"))
                .thenReturn(Optional.of(channel));
        when(context.applicationRepository.findAllByTenantIdOrderByCreatedAtDesc("tenant-1"))
                .thenReturn(List.of(application));

        DepositPaymentService.C2bValidationDecision decision =
                context.service.validateDepositCallback("tenant-1", "600111", "254700000000");

        assertTrue(decision.accepted());
        assertEquals("app-1", decision.matchedApplicationId());
        verify(context.depositPaymentRepository, never())
                .existsByTenantIdAndMatchedApplicationIdAndStatusIn(any(), any(), any());
    }

    @Test
    void validateDepositCallbackRejectsWhenNoApplicationMatches() {
        TestContext context = new TestContext();
        TenantPaymentChannel channel = activeChannel("tenant-1", "600111");
        when(context.channelRepository.findByTenantIdAndShortCodeAndActiveTrue("tenant-1", "600111"))
                .thenReturn(Optional.of(channel));
        when(context.applicationRepository.findAllByTenantIdOrderByCreatedAtDesc("tenant-1"))
                .thenReturn(List.of());

        DepositPaymentService.C2bValidationDecision decision =
                context.service.validateDepositCallback("tenant-1", "600111", "0700000000");

        assertFalse(decision.accepted());
        assertEquals("C2B00011", decision.resultCode());
        assertEquals("Rejected", decision.resultDesc());
        verify(context.depositPaymentRepository, never())
                .existsByTenantIdAndMatchedApplicationIdAndStatusIn(any(), any(), any());
    }

    @Test
    void acceptDepositCallbackStoresDepositWhenLoanNotActivated() {
        TestContext context = new TestContext();
        TenantPaymentChannel channel = activeChannel("tenant-1", "600111");
        LoanRequestApplication application = application("app-1", "tenant-1", "0700000000", ApplicationStatus.OFFER_SELECTED, null);
        when(context.channelRepository.findByTenantIdAndShortCodeAndActiveTrue("tenant-1", "600111"))
                .thenReturn(Optional.of(channel));
        when(context.depositPaymentRepository.existsByMpesaReceiptNumber("ABC123")).thenReturn(false);
        when(context.applicationRepository.findAllByTenantIdOrderByCreatedAtDesc("tenant-1"))
                .thenReturn(List.of(application));
        when(context.depositPaymentRepository.save(any(DepositPayment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DepositPayment payment = context.service.acceptDepositCallback(
                "tenant-1",
                "Pay Bill",
                "ABC123",
                "20260805120000",
                new BigDecimal("500.00"),
                "600111",
                "0700000000",
                "254700000000",
                "Jane",
                null,
                "Doe",
                "{}");

        assertEquals(DepositPaymentStatus.MATCHED, payment.getStatus());
        assertEquals("app-1", payment.getMatchedApplicationId());
        verify(context.mpesaPaymentService, never()).acceptMatchedDarajaCallback(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void acceptDepositCallbackRoutesActivatedLoanToRepaymentQueue() {
        TestContext context = new TestContext();
        TenantPaymentChannel channel = activeChannel("tenant-1", "600111");
        LoanRequestApplication application = application(
                "app-1",
                "tenant-1",
                "0700000000",
                ApplicationStatus.FINERACT_LOAN_ACTIVATED,
                "loan-42");
        when(context.channelRepository.findByTenantIdAndShortCodeAndActiveTrue("tenant-1", "600111"))
                .thenReturn(Optional.of(channel));
        when(context.depositPaymentRepository.existsByMpesaReceiptNumber("ABC123")).thenReturn(false);
        when(context.applicationRepository.findAllByTenantIdOrderByCreatedAtDesc("tenant-1"))
                .thenReturn(List.of(application));

        DepositPayment payment = context.service.acceptDepositCallback(
                "tenant-1",
                "Pay Bill",
                "ABC123",
                "20260805120000",
                new BigDecimal("500.00"),
                "600111",
                "0700000000",
                "254700000000",
                "Jane",
                null,
                "Doe",
                "{}");

        assertNull(payment);
        verify(context.mpesaPaymentService).acceptMatchedDarajaCallback(
                eq(application),
                eq("ABC123"),
                eq("20260805120000"),
                eq(new BigDecimal("500.00")),
                eq("600111"),
                eq("0700000000"),
                eq("254700000000"),
                eq("Jane"),
                eq(null),
                eq("Doe"),
                eq("{}"));
        verify(context.depositPaymentRepository, never()).save(any());
    }

    private static TenantPaymentChannel activeChannel(String tenantId, String shortCode) {
        TenantPaymentChannel channel = new TenantPaymentChannel();
        channel.setTenantId(tenantId);
        channel.setShortCode(shortCode);
        channel.setActive(true);
        channel.setChannelType(PaymentChannelType.MPESA_PAYBILL);
        return channel;
    }

    private static LoanRequestApplication application(
            String id,
            String tenantId,
            String phoneNumber,
            ApplicationStatus status,
            String fineractLoanId) {
        LoanRequestApplication application = new LoanRequestApplication();
        setId(application, id);
        application.setTenantId(tenantId);
        application.setPhoneNumber(phoneNumber);
        application.setStatus(status);
        application.setFineractLoanId(fineractLoanId);
        application.setFineractClientId("client-1");
        return application;
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
        private final DepositPaymentRepository depositPaymentRepository = mock(DepositPaymentRepository.class);
        private final TenantPaymentChannelRepository channelRepository = mock(TenantPaymentChannelRepository.class);
        private final LoanRequestApplicationRepository applicationRepository = mock(LoanRequestApplicationRepository.class);
        private final MpesaPaymentService mpesaPaymentService = mock(MpesaPaymentService.class);
        private final DepositPaymentService service = new DepositPaymentService(
                depositPaymentRepository,
                channelRepository,
                applicationRepository,
                mpesaPaymentService);
    }
}
