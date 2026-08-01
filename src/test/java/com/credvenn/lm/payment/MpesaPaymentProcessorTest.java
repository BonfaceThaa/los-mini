package com.credvenn.lm.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.credvenn.lm.application.ApplicationService;
import com.credvenn.lm.application.ApplicationStatus;
import com.credvenn.lm.application.LoanRequestApplication;
import com.credvenn.lm.application.LoanRequestApplicationRepository;
import com.credvenn.lm.devicecontrol.LoanRepaymentPostedEvent;
import com.credvenn.lm.fineract.FineractGateway;
import com.credvenn.lm.tenant.Tenant;
import com.credvenn.lm.tenant.TenantService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class MpesaPaymentProcessorTest {

    @Test
    void processMarksApplicationClosedWhenRepaymentClosesLoanInFineract() {
        TestContext context = new TestContext();
        MpesaPaymentReceipt receipt = receipt("receipt-1", "600111", "254700000000");
        LoanRequestApplication application = application("app-1", "tenant-1", "254700000000", "loan-42");
        Tenant tenant = new Tenant();
        tenant.setId("tenant-1");

        when(context.receiptRepository.findById("receipt-1")).thenReturn(Optional.of(receipt));
        when(context.channelRepository.findByShortCodeAndActiveTrue("600111"))
                .thenReturn(Optional.of(channel("tenant-1", "600111")));
        when(context.tenantService.getRequiredTenant("tenant-1")).thenReturn(tenant);
        when(context.applicationRepository.findAllByTenantIdAndStatusAndFineractLoanIdIsNotNullOrderByCreatedAtDesc(
                "tenant-1",
                ApplicationStatus.FINERACT_LOAN_ACTIVATED)).thenReturn(List.of(application));
        when(context.fineractGateway.getLoanSummary(tenant, "loan-42"))
                .thenReturn(
                        new FineractGateway.LoanSummary("loan-42", true, "loanStatusType.active"),
                        new FineractGateway.LoanSummary("loan-42", false, "loanStatusType.closed"));
        when(context.fineractGateway.postLoanRepayment(eq(tenant), eq("loan-42"), any())).thenReturn("txn-1");

        context.processor.process("receipt-1");

        assertEquals(MpesaPaymentProcessingStatus.REPAYMENT_POSTED, receipt.getProcessingStatus());
        assertEquals("txn-1", receipt.getFineractTransactionId());
        verify(context.applicationService).markLoanClosed(
                "tenant-1",
                "app-1",
                "system",
                "Loan fully repaid and closed in Fineract");
        verify(context.eventPublisher).publishEvent(any(LoanRepaymentPostedEvent.class));
    }

    @Test
    void processDoesNotMarkApplicationClosedWhenLoanRemainsActive() {
        TestContext context = new TestContext();
        MpesaPaymentReceipt receipt = receipt("receipt-2", "600111", "254700000000");
        LoanRequestApplication application = application("app-2", "tenant-1", "254700000000", "loan-43");
        Tenant tenant = new Tenant();
        tenant.setId("tenant-1");

        when(context.receiptRepository.findById("receipt-2")).thenReturn(Optional.of(receipt));
        when(context.channelRepository.findByShortCodeAndActiveTrue("600111"))
                .thenReturn(Optional.of(channel("tenant-1", "600111")));
        when(context.tenantService.getRequiredTenant("tenant-1")).thenReturn(tenant);
        when(context.applicationRepository.findAllByTenantIdAndStatusAndFineractLoanIdIsNotNullOrderByCreatedAtDesc(
                "tenant-1",
                ApplicationStatus.FINERACT_LOAN_ACTIVATED)).thenReturn(List.of(application));
        when(context.fineractGateway.getLoanSummary(tenant, "loan-43"))
                .thenReturn(
                        new FineractGateway.LoanSummary("loan-43", true, "loanStatusType.active"),
                        new FineractGateway.LoanSummary("loan-43", true, "loanStatusType.active"));
        when(context.fineractGateway.postLoanRepayment(eq(tenant), eq("loan-43"), any())).thenReturn("txn-2");

        context.processor.process("receipt-2");

        assertEquals(MpesaPaymentProcessingStatus.REPAYMENT_POSTED, receipt.getProcessingStatus());
        verify(context.applicationService, never()).markLoanClosed(any(), any(), any(), any());
    }

    private static MpesaPaymentReceipt receipt(String id, String shortCode, String phone) {
        MpesaPaymentReceipt receipt = new MpesaPaymentReceipt();
        setId(receipt, id);
        receipt.setBusinessShortCode(shortCode);
        receipt.setNormalizedPhoneNumber(phone);
        receipt.setTransactionTime(LocalDateTime.of(2026, 6, 26, 10, 15).atZone(ZoneId.of("Africa/Nairobi")).toInstant());
        receipt.setTransactionAmount(new BigDecimal("500.00"));
        receipt.setMpesaReceiptNumber("RCP123");
        receipt.setMsisdn(phone);
        return receipt;
    }

    private static LoanRequestApplication application(String id, String tenantId, String phone, String fineractLoanId) {
        LoanRequestApplication application = new LoanRequestApplication();
        setId(application, id);
        application.setTenantId(tenantId);
        application.setPhoneNumber(phone);
        application.setStatus(ApplicationStatus.FINERACT_LOAN_ACTIVATED);
        application.setFineractLoanId(fineractLoanId);
        application.setFineractClientId("client-1");
        application.setInstallmentAmount(new BigDecimal("500.00"));
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

    private static void setId(Object target, String id) {
        try {
            var field = target.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(target, id);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static final class TestContext {
        private final MpesaPaymentReceiptRepository receiptRepository = mock(MpesaPaymentReceiptRepository.class);
        private final TenantPaymentChannelRepository channelRepository = mock(TenantPaymentChannelRepository.class);
        private final LoanRequestApplicationRepository applicationRepository = mock(LoanRequestApplicationRepository.class);
        private final TenantService tenantService = mock(TenantService.class);
        private final FineractGateway fineractGateway = mock(FineractGateway.class);
        private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        private final ApplicationService applicationService = mock(ApplicationService.class);
        private final MpesaPaymentProcessor processor = new MpesaPaymentProcessor(
                receiptRepository,
                channelRepository,
                applicationRepository,
                tenantService,
                fineractGateway,
                eventPublisher,
                applicationService);
    }
}
