package com.credvenn.lm.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.credvenn.lm.client.ClientRecordService;
import com.credvenn.lm.client.ClientRecord;
import com.credvenn.lm.fineract.FineractGateway;
import com.credvenn.lm.inventory.InventoryDeviceAssignment;
import com.credvenn.lm.inventory.InventoryDeviceAssignmentRepository;
import com.credvenn.lm.loanproduct.LoanProductMappingRepository;
import com.credvenn.lm.payment.DepositPaymentRepository;
import com.credvenn.lm.payment.DepositPaymentStatus;
import com.credvenn.lm.statement.StatementAnalysisRepository;
import com.credvenn.lm.subscription.SubscriptionBillingService;
import com.credvenn.lm.subscription.SubscriptionGuardService;
import com.credvenn.lm.tenant.Tenant;
import com.credvenn.lm.tenant.TenantService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class ApplicationServiceTest {

    @Test
    void createPersistsAndReturnsNewApplicantFields() {
        TestContext context = new TestContext();
        doNothing().when(context.subscriptionGuardService).assertCanCreateApplication("tenant-1");
        when(context.applicationRepository.save(any(LoanRequestApplication.class))).thenAnswer(invocation -> {
            LoanRequestApplication application = invocation.getArgument(0);
            application.assignId();
            return application;
        });
        when(context.statusHistoryRepository.save(any(ApplicationStatusHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(context.statusHistoryRepository.findAllByApplicationIdOrderByIdAsc(anyString())).thenReturn(List.of());
        when(context.applicationRepository.findByIdAndTenantId(anyString(), anyString())).thenAnswer(invocation -> {
            String applicationId = invocation.getArgument(0);
            LoanRequestApplication application = new LoanRequestApplication();
            application.assignId();
            setId(application, applicationId);
            application.setTenantId(invocation.getArgument(1));
            application.setApplicantFirstName("Jane");
            application.setApplicantMiddleName("Wanjiku");
            application.setApplicantLastName("Doe");
            application.setPhoneNumber("254700000000");
            application.setNationalId("32162157");
            application.setApplicantIdType(ApplicantIdType.NATIONAL_ID);
            application.setDob(LocalDate.of(1995, 5, 10));
            application.setGender("Female");
            application.setStatementOtp("432198");
            application.setRequestedAmount(new BigDecimal("1000.00"));
            application.setRequestedTermMonths(12);
            application.setStatus(ApplicationStatus.PENDING_KYC);
            return Optional.of(application);
        });

        ApplicationDtos.CreateLoanRequestApplicationRequest request = new ApplicationDtos.CreateLoanRequestApplicationRequest(
                "Jane",
                " Wanjiku ",
                "Doe",
                "254700000000",
                "32162157",
                ApplicantIdType.NATIONAL_ID,
                LocalDate.of(1995, 5, 10),
                " Female ",
                " 432198 ",
                new BigDecimal("1000.00"),
                12);

        ApplicationDtos.LoanRequestApplicationResponse response = context.service.create("tenant-1", "tester", request);

        ArgumentCaptor<LoanRequestApplication> applicationCaptor = ArgumentCaptor.forClass(LoanRequestApplication.class);
        verify(context.applicationRepository).save(applicationCaptor.capture());
        LoanRequestApplication saved = applicationCaptor.getValue();
        assertEquals("Wanjiku", saved.getApplicantMiddleName());
        assertEquals(LocalDate.of(1995, 5, 10), saved.getDob());
        assertEquals("Female", saved.getGender());
        assertEquals("432198", saved.getStatementOtp());

        assertEquals("Wanjiku", response.applicantMiddleName());
        assertEquals(LocalDate.of(1995, 5, 10), response.dob());
        assertEquals("Female", response.gender());
        assertEquals("432198", response.statementOtp());
    }

    @Test
    void createNormalizesBlankOptionalApplicantFieldsToNull() {
        TestContext context = new TestContext();
        doNothing().when(context.subscriptionGuardService).assertCanCreateApplication("tenant-1");
        when(context.applicationRepository.save(any(LoanRequestApplication.class))).thenAnswer(invocation -> {
            LoanRequestApplication application = invocation.getArgument(0);
            application.assignId();
            return application;
        });
        when(context.statusHistoryRepository.save(any(ApplicationStatusHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(context.statusHistoryRepository.findAllByApplicationIdOrderByIdAsc(anyString())).thenReturn(List.of());

        LoanRequestApplication persisted = new LoanRequestApplication();
        persisted.assignId();
        setId(persisted, "app-1");
        persisted.setTenantId("tenant-1");
        persisted.setApplicantFirstName("Jane");
        persisted.setApplicantLastName("Doe");
        persisted.setPhoneNumber("254700000000");
        persisted.setNationalId("32162157");
        persisted.setApplicantIdType(ApplicantIdType.NATIONAL_ID);
        persisted.setRequestedAmount(new BigDecimal("1000.00"));
        persisted.setRequestedTermMonths(12);
        persisted.setStatus(ApplicationStatus.PENDING_KYC);
        when(context.applicationRepository.findByIdAndTenantId(anyString(), anyString())).thenReturn(Optional.of(persisted));

        ApplicationDtos.CreateLoanRequestApplicationRequest request = new ApplicationDtos.CreateLoanRequestApplicationRequest(
                "Jane",
                "   ",
                "Doe",
                "254700000000",
                "32162157",
                ApplicantIdType.NATIONAL_ID,
                null,
                "   ",
                "   ",
                new BigDecimal("1000.00"),
                12);

        context.service.create("tenant-1", "tester", request);

        ArgumentCaptor<LoanRequestApplication> applicationCaptor = ArgumentCaptor.forClass(LoanRequestApplication.class);
        verify(context.applicationRepository).save(applicationCaptor.capture());
        LoanRequestApplication saved = applicationCaptor.getValue();
        assertNull(saved.getApplicantMiddleName());
        assertNull(saved.getDob());
        assertNull(saved.getGender());
        assertNull(saved.getStatementOtp());
    }

    @Test
    void createReusesExistingClientRecordMatchedByNationalId() {
        TestContext context = new TestContext();
        doNothing().when(context.subscriptionGuardService).assertCanCreateApplication("tenant-1");
        ClientRecord clientRecord = new ClientRecord();
        clientRecord.setFineractClientId("fineract-client-7");
        when(context.clientRecordService.findByTenantIdAndNationalId("tenant-1", "32162157")).thenReturn(clientRecord);
        when(context.applicationRepository.save(any(LoanRequestApplication.class))).thenAnswer(invocation -> {
            LoanRequestApplication application = invocation.getArgument(0);
            application.assignId();
            return application;
        });
        when(context.statusHistoryRepository.save(any(ApplicationStatusHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(context.statusHistoryRepository.findAllByApplicationIdOrderByIdAsc(anyString())).thenReturn(List.of());
        LoanRequestApplication persisted = new LoanRequestApplication();
        persisted.assignId();
        setId(persisted, "app-7");
        persisted.setTenantId("tenant-1");
        persisted.setApplicantFirstName("Jane");
        persisted.setApplicantLastName("Doe");
        persisted.setPhoneNumber("254700000000");
        persisted.setNationalId("32162157");
        persisted.setApplicantIdType(ApplicantIdType.NATIONAL_ID);
        persisted.setRequestedAmount(new BigDecimal("1000.00"));
        persisted.setRequestedTermMonths(12);
        persisted.setFineractClientId("fineract-client-7");
        persisted.setStatus(ApplicationStatus.PENDING_KYC);
        when(context.applicationRepository.findByIdAndTenantId(anyString(), anyString())).thenReturn(Optional.of(persisted));

        ApplicationDtos.CreateLoanRequestApplicationRequest request = new ApplicationDtos.CreateLoanRequestApplicationRequest(
                "Jane",
                null,
                "Doe",
                "254700000000",
                "32162157",
                ApplicantIdType.NATIONAL_ID,
                null,
                null,
                null,
                new BigDecimal("1000.00"),
                12);

        context.service.create("tenant-1", "tester", request);

        ArgumentCaptor<LoanRequestApplication> applicationCaptor = ArgumentCaptor.forClass(LoanRequestApplication.class);
        verify(context.applicationRepository).save(applicationCaptor.capture());
        assertEquals("fineract-client-7", applicationCaptor.getValue().getFineractClientId());
    }

    @Test
    void calculateInstallmentAmountUsesSimpleMonthlyInterest() {
        TestContext context = new TestContext();

        BigDecimal installment = invokeCalculateInstallmentAmount(
                context.service,
                new BigDecimal("11899.15"),
                new BigDecimal("16"),
                2,
                4,
                4);

        assertEquals(new BigDecimal("4878.65"), installment);
    }

    @Test
    void calculateInstallmentAmountProratesYearlyInterestByTerm() {
        TestContext context = new TestContext();

        BigDecimal installment = invokeCalculateInstallmentAmount(
                context.service,
                new BigDecimal("11899.15"),
                new BigDecimal("16"),
                3,
                4,
                4);

        assertEquals(new BigDecimal("3133.44"), installment);
    }

    @Test
    void calculateInstallmentAmountUsesRepaymentCountAsDivisor() {
        TestContext context = new TestContext();

        BigDecimal installment = invokeCalculateInstallmentAmount(
                context.service,
                new BigDecimal("10800"),
                new BigDecimal("12"),
                2,
                4,
                120);

        assertEquals(new BigDecimal("133.20"), installment);
    }

    @Test
    void activateLoanPublishesLoanActivatedEventWhenStatusBecomesFineractLoanActivated() {
        TestContext context = new TestContext();
        LoanRequestApplication application = new LoanRequestApplication();
        setId(application, "app-1");
        application.setTenantId("tenant-1");
        application.setStatus(ApplicationStatus.FINERACT_LOAN_CREATED_PENDING_DEVICE);
        application.setFineractLoanId("loan-42");
        application.setAssignedDeviceId("device-1");
        application.setAssignedDeviceImei1("imei-1");

        when(context.applicationRepository.findByIdAndTenantId("app-1", "tenant-1")).thenReturn(Optional.of(application));
        when(context.depositPaymentRepository.existsByTenantIdAndMatchedApplicationIdAndStatusIn(
                eq("tenant-1"),
                eq("app-1"),
                eq(List.of(DepositPaymentStatus.MATCHED)))).thenReturn(true);
        when(context.assignmentRepository.findByApplicationId("app-1")).thenReturn(Optional.of(new InventoryDeviceAssignment()));
        when(context.tenantService.getRequiredTenant("tenant-1")).thenReturn(new Tenant());
        when(context.statusHistoryRepository.save(any(ApplicationStatusHistory.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(context.statusHistoryRepository.findAllByApplicationIdOrderByIdAsc("app-1")).thenReturn(List.of());

        context.service.activateLoan("tenant-1", "app-1", "approver");

        ArgumentCaptor<LoanActivatedEvent> eventCaptor = ArgumentCaptor.forClass(LoanActivatedEvent.class);
        verify(context.applicationEventPublisher).publishEvent(eventCaptor.capture());
        LoanActivatedEvent event = eventCaptor.getValue();
        assertEquals("tenant-1", event.tenantId());
        assertEquals("app-1", event.applicationId());
        assertEquals("loan-42", event.fineractLoanId());
        assertEquals("approver", event.actor());
        assertEquals(ApplicationStatus.FINERACT_LOAN_ACTIVATED, application.getStatus());
        verify(context.fineractGateway).activateLoan(any(Tenant.class), eq(application));
        verify(context.subscriptionBillingService).evaluateNextCyclePricingMode("tenant-1");
    }

    @Test
    void activateLoanDoesNotPublishEventWhenFinalApprovalPreconditionsFail() {
        TestContext context = new TestContext();
        LoanRequestApplication application = new LoanRequestApplication();
        setId(application, "app-2");
        application.setTenantId("tenant-1");
        application.setStatus(ApplicationStatus.FINERACT_LOAN_CREATED_PENDING_DEVICE);
        application.setFineractLoanId("loan-43");

        when(context.applicationRepository.findByIdAndTenantId("app-2", "tenant-1")).thenReturn(Optional.of(application));
        when(context.depositPaymentRepository.existsByTenantIdAndMatchedApplicationIdAndStatusIn(
                eq("tenant-1"),
                eq("app-2"),
                eq(List.of(DepositPaymentStatus.MATCHED)))).thenReturn(false);

        try {
            context.service.activateLoan("tenant-1", "app-2", "approver");
        } catch (Exception ignored) {
            // Expected because deposit payment is required before final approval.
        }

        verify(context.applicationEventPublisher, never()).publishEvent(any());
        verify(context.fineractGateway, never()).activateLoan(any(Tenant.class), any(LoanRequestApplication.class));
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

    private static BigDecimal invokeCalculateInstallmentAmount(
            ApplicationService service,
            BigDecimal principal,
            BigDecimal interestRatePerPeriod,
            Integer interestRateFrequencyType,
            int termInMonths,
            int numberOfRepayments) {
        try {
            var method = ApplicationService.class.getDeclaredMethod(
                    "calculateInstallmentAmount",
                    BigDecimal.class,
                    BigDecimal.class,
                    Integer.class,
                    int.class,
                    int.class);
            method.setAccessible(true);
            return (BigDecimal) method.invoke(service, principal, interestRatePerPeriod, interestRateFrequencyType, termInMonths, numberOfRepayments);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static final class TestContext {
        private final LoanRequestApplicationRepository applicationRepository = mock(LoanRequestApplicationRepository.class);
        private final ApplicationStatusHistoryRepository statusHistoryRepository = mock(ApplicationStatusHistoryRepository.class);
        private final SubscriptionGuardService subscriptionGuardService = mock(SubscriptionGuardService.class);
        private final TenantService tenantService = mock(TenantService.class);
        private final FineractGateway fineractGateway = mock(FineractGateway.class);
        private final DepositPaymentRepository depositPaymentRepository = mock(DepositPaymentRepository.class);
        private final InventoryDeviceAssignmentRepository assignmentRepository = mock(InventoryDeviceAssignmentRepository.class);
        private final ApplicationEventPublisher applicationEventPublisher = mock(ApplicationEventPublisher.class);
        private final SubscriptionBillingService subscriptionBillingService = mock(SubscriptionBillingService.class);
        private final ClientRecordService clientRecordService = mock(ClientRecordService.class);
        private final ApplicationService service = new ApplicationService(
                applicationRepository,
                statusHistoryRepository,
                mock(StatementAnalysisRepository.class),
                tenantService,
                fineractGateway,
                mock(LoanProductMappingRepository.class),
                depositPaymentRepository,
                clientRecordService,
                assignmentRepository,
                applicationEventPublisher,
                subscriptionGuardService,
                subscriptionBillingService);
    }
}
