package com.credvenn.lm.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.credvenn.lm.applicationvariable.ApplicationVariableService;
import com.credvenn.lm.client.ClientRecordService;
import com.credvenn.lm.common.exception.BadRequestException;
import com.credvenn.lm.common.exception.NotFoundException;
import com.credvenn.lm.fineract.FineractGateway;
import com.credvenn.lm.inventory.*;
import com.credvenn.lm.kyc.*;
import com.credvenn.lm.loanproduct.*;
import com.credvenn.lm.payment.DepositPaymentRepository;
import com.credvenn.lm.statement.*;
import com.credvenn.lm.subscription.*;
import com.credvenn.lm.tenant.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

/** Characterizes the phone journey through public service operations before profile support. */
@ExtendWith(MockitoExtension.class)
class PhoneOriginationBaselineTest {
    @Mock LoanRequestApplicationRepository applications;
    @Mock ApplicationStatusHistoryRepository history;
    @Mock KycCheckRepository kyc;
    @Mock StatementReviewRepository reviews;
    @Mock StatementAnalysisRepository analyses;
    @Mock TenantService tenants;
    @Mock FineractGateway fineract;
    @Mock LoanProductMappingRepository products;
    @Mock DepositPaymentRepository deposits;
    @Mock ClientRecordService clients;
    @Mock InventoryDeviceAssignmentRepository assignments;
    @Mock ApplicationStatementOtpService otps;
    @Mock ApplicationEventPublisher events;
    @Mock SubscriptionGuardService subscriptions;
    @Mock SubscriptionBillingService billing;
    @Mock ApplicationVariableService variables;
    @Mock com.credvenn.lm.origination.OriginationProfileResolver profiles;
    @InjectMocks ApplicationService service;

    private LoanRequestApplication application;
    private Tenant tenant;

    @BeforeEach
    void setup() {
        application = new LoanRequestApplication();
        ReflectionTestUtils.setField(application, "id", "app-1");
        application.setTenantId("tenant-1");
        application.setRequestedAmount(new BigDecimal("20000"));
        application.setStatus(ApplicationStatus.STATEMENT_VERIFIED);
        application.setFineractClientId("client-1");
        tenant = new Tenant();
        tenant.setStatementAnalysisMode(TenantStatementAnalysisMode.AUTO);
    }

    private void loadApplication() {
        when(applications.findByIdAndTenantId("app-1", "tenant-1")).thenReturn(Optional.of(application));
    }

    private void readiness(KycStatus status, boolean statementPassed) {
        loadApplication();
        when(tenants.getRequiredTenant("tenant-1")).thenReturn(tenant);
        KycCheck check = new KycCheck();
        check.setStatus(status);
        when(kyc.findFirstByApplicationIdOrderByCreatedAtDesc("app-1")).thenReturn(Optional.of(check));
        if (tenant.getStatementAnalysisMode() != TenantStatementAnalysisMode.DISABLED) {
            StatementAnalysis analysis = new StatementAnalysis();
            analysis.setStatus(statementPassed ? StatementAnalysisStatus.PASSED : StatementAnalysisStatus.FAILED);
            when(analyses.findFirstByApplicationIdOrderByCreatedAtDesc("app-1")).thenReturn(Optional.of(analysis));
        }
    }

    private LoanProductMapping product() {
        LoanProductMapping product = new LoanProductMapping();
        product.setTenantId("tenant-1");
        product.setProductCode("PHONE_STANDARD");
        product.setFineractProductId(7L);
        product.setDisplayName("Phone standard");
        product.setPrincipalMin(new BigDecimal("10000"));
        product.setPrincipalMax(new BigDecimal("30000"));
        product.setNumberOfRepayments(4);
        product.setRepaymentEvery(1);
        product.setRepaymentFrequency("MONTHS");
        product.setInterestRateFrequency("MONTHS");
        product.setInterestRatePerPeriod(BigDecimal.ZERO);
        product.setActive(true);
        return product;
    }

    @ParameterizedTest
    @CsvSource({"PASSED,true,true,true", "MANUALLY_APPROVED,true,true,true",
            "FAILED,true,true,false", "PASSED,false,true,false", "PASSED,true,false,false"})
    void offersRequireKycClientAndStatement(KycStatus status, boolean statementPassed,
            boolean clientExists, boolean expectedReady) {
        if (!clientExists) application.setFineractClientId(null);
        readiness(status, statementPassed);
        if (expectedReady) when(products.findAllByTenantIdAndActiveTrueOrderByDisplayNameAsc("tenant-1"))
                .thenReturn(List.of(product()));
        var response = service.getEligibleProducts("tenant-1", "app-1");
        assertEquals(expectedReady, response.offersReady());
        assertEquals(expectedReady ? 1 : 0, response.products().size());
        if (!expectedReady) verifyNoInteractions(products);
        verifyNoInteractions(fineract);
    }

    @Test
    void disabledStatementAnalysisAllowsOffersWithoutStatementRecords() {
        tenant.setStatementAnalysisMode(TenantStatementAnalysisMode.DISABLED);
        readiness(KycStatus.PASSED, false);
        when(products.findAllByTenantIdAndActiveTrueOrderByDisplayNameAsc("tenant-1"))
                .thenReturn(List.of(product()));
        assertTrue(service.getEligibleProducts("tenant-1", "app-1").offersReady());
        verifyNoInteractions(analyses, reviews);
    }

    @Test
    void latestManualRejectionBlocksOffersWithoutConsultingProviderResult() {
        loadApplication();
        when(tenants.getRequiredTenant("tenant-1")).thenReturn(tenant);
        KycCheck check = new KycCheck();
        check.setStatus(KycStatus.PASSED);
        when(kyc.findFirstByApplicationIdOrderByCreatedAtDesc("app-1")).thenReturn(Optional.of(check));
        StatementReview review = new StatementReview();
        review.setDecision(StatementReviewDecision.REJECTED);
        when(reviews.findFirstByApplicationIdOrderByCreatedAtDesc("app-1")).thenReturn(Optional.of(review));
        assertFalse(service.getEligibleProducts("tenant-1", "app-1").offersReady());
        verifyNoInteractions(analyses, products, fineract);
    }

    @ParameterizedTest
    @CsvSource({"9999.99,0", "10000,1", "30000,1", "30000.01,0"})
    void productAmountLimitsAreInclusive(String amount, int expectedCount) {
        application.setRequestedAmount(new BigDecimal(amount));
        readiness(KycStatus.PASSED, true);
        when(products.findAllByTenantIdAndActiveTrueOrderByDisplayNameAsc("tenant-1"))
                .thenReturn(List.of(product()));
        assertEquals(expectedCount, service.getEligibleProducts("tenant-1", "app-1").products().size());
    }

    @Test
    void selectingEligibleOfferRecordsSelectionAndHistoryWithoutCreatingLoan() {
        readiness(KycStatus.PASSED, true);
        when(products.findAllByTenantIdAndActiveTrueOrderByDisplayNameAsc("tenant-1"))
                .thenReturn(List.of(product()));
        var response = service.selectOffer("tenant-1", "app-1", "officer",
                new ApplicationDtos.SelectOfferRequest("7"));
        assertEquals("7", response.selectedFineractProductId());
        assertEquals(ApplicationStatus.OFFER_SELECTED, response.status());
        assertNotNull(application.getSelectedOfferAt());
        var captured = ArgumentCaptor.forClass(ApplicationStatusHistory.class);
        verify(history).save(captured.capture());
        assertEquals("OFFER_SELECTED", captured.getValue().getToStatus());
        assertEquals("officer", captured.getValue().getChangedBy());
        verifyNoInteractions(fineract);
    }

    @Test
    void selectingProductOutsideEligibleCatalogDoesNotMutateApplication() {
        readiness(KycStatus.PASSED, true);
        when(products.findAllByTenantIdAndActiveTrueOrderByDisplayNameAsc("tenant-1"))
                .thenReturn(List.of(product()));
        assertThrows(BadRequestException.class, () -> service.selectOffer("tenant-1", "app-1", "officer",
                new ApplicationDtos.SelectOfferRequest("99")));
        assertNull(application.getSelectedFineractProductId());
        assertEquals(ApplicationStatus.STATEMENT_VERIFIED, application.getStatus());
        verifyNoInteractions(history, fineract);
    }

    @Test
    void otherTenantCannotLoadApplicationForOfferSelection() {
        assertThrows(NotFoundException.class, () -> service.selectOffer("tenant-2", "app-1", "officer",
                new ApplicationDtos.SelectOfferRequest("7")));
        verify(applications).findByIdAndTenantId("app-1", "tenant-2");
        verifyNoInteractions(products, fineract, history);
    }

    @ParameterizedTest
    @CsvSource({"PERCENTAGE,20", "FIXED_AMOUNT,5000"})
    void deviceAssignmentComputesPhonePrincipalAndRepayments(DepositType depositType, String depositValue) {
        loadApplication();
        application.setSelectedFineractProductId("7");
        when(products.findAllByTenantIdAndActiveTrueOrderByDisplayNameAsc("tenant-1"))
                .thenReturn(List.of(product()));
        InventoryDevice device = new InventoryDevice();
        ReflectionTestUtils.setField(device, "id", "device-1");
        device.setCashPrice(new BigDecimal("25000"));
        device.setDepositType(depositType);
        device.setDepositValue(new BigDecimal(depositValue));
        service.handleDeviceAssigned("tenant-1", "app-1", "officer", device);
        assertEquals(new BigDecimal("5000.00"), application.getDepositAmount());
        assertEquals(new BigDecimal("20000.00"), application.getApprovedAmount());
        assertEquals(new BigDecimal("5000.00"), application.getInstallmentAmount());
        assertEquals(new BigDecimal("25000.00"), application.getTotalPayment());
        assertEquals(4, application.getApprovedTermMonths());
        assertEquals("7", application.getApprovedFineractProductId());
        assertEquals("device-1", application.getAssignedDeviceId());
        assertEquals(ApplicationStatus.DEVICE_ASSIGNED, application.getStatus());
        verifyNoInteractions(fineract);
    }

    @ParameterizedTest
    @CsvSource({"false,true,false", "true,false,false", "true,true,false", "true,true,true"})
    void approvalRejectsMissingConsentClientDeviceOrPricing(boolean consent, boolean client, boolean device) {
        loadApplication();
        application.setConsentCaptured(consent);
        if (!client) application.setFineractClientId(null);
        if (consent && client && device) when(assignments.findByApplicationId("app-1"))
                .thenReturn(Optional.of(new InventoryDeviceAssignment()));
        assertThrows(BadRequestException.class, () -> service.internalApprove("tenant-1", "app-1", "approver",
                new ApplicationDtos.InternalApprovalRequest("Approved")));
        assertFalse(application.isInternalApproved());
        verifyNoInteractions(fineract, history);
    }

    @Test
    void internalApprovalCreatesPendingLoanUsingDeviceFinancing() {
        loadApplication();
        application.setConsentCaptured(true);
        application.setApprovedAmount(new BigDecimal("20000.00"));
        application.setApprovedTermMonths(4);
        application.setApprovedFineractProductId("7");
        when(assignments.findByApplicationId("app-1")).thenReturn(Optional.of(new InventoryDeviceAssignment()));
        when(products.findAllByTenantIdAndActiveTrueOrderByDisplayNameAsc("tenant-1"))
                .thenReturn(List.of(product()));
        when(tenants.getRequiredTenant("tenant-1")).thenReturn(tenant);
        when(fineract.createPendingLoan(eq(tenant), eq(application), any(),
                eq(new BigDecimal("20000.00")), eq(4))).thenReturn("loan-7");
        var response = service.internalApprove("tenant-1", "app-1", "approver",
                new ApplicationDtos.InternalApprovalRequest(" Verified financing "));
        assertTrue(response.internalApproved());
        assertEquals("approver", application.getApprovedBy());
        assertEquals("Verified financing", application.getApprovalReason());
        assertEquals("loan-7", response.fineractLoanId());
        assertEquals(ApplicationStatus.FINERACT_LOAN_CREATED_PENDING_DEVICE, response.status());
        verify(fineract, never()).activateLoan(any(), any());
    }

    @Test
    void offerSelectionCannotBypassReadiness() {
        readiness(KycStatus.FAILED, true);
        assertThrows(BadRequestException.class, () -> service.selectOffer("tenant-1", "app-1", "officer",
                new ApplicationDtos.SelectOfferRequest("7")));
        assertNull(application.getSelectedFineractProductId());
        verifyNoInteractions(products, history, fineract);
    }

    @Test
    void activationRequiresPendingLoanBeforeCheckingPayments() {
        loadApplication();
        var error = assertThrows(BadRequestException.class,
                () -> service.activateLoan("tenant-1", "app-1", "officer"));
        assertEquals("Pending Fineract loan has not been created", error.getMessage());
        verifyNoInteractions(deposits, assignments, fineract, events, billing);
    }

    @Test
    void matchedDepositCannotActivateLoanWithoutDeviceAssignment() {
        loadApplication();
        application.setFineractLoanId("loan-7");
        when(deposits.existsByTenantIdAndMatchedApplicationIdAndStatusIn(eq("tenant-1"), eq("app-1"),
                eq(List.of(com.credvenn.lm.payment.DepositPaymentStatus.MATCHED)))).thenReturn(true);
        var error = assertThrows(BadRequestException.class,
                () -> service.activateLoan("tenant-1", "app-1", "officer"));
        assertEquals("Device assignment is required before activation", error.getMessage());
        verifyNoInteractions(fineract, events, billing);
    }
    @Test
    void invalidQuestionnaireStopsCreationBeforePersistenceOrEvents() {
        when(profiles.resolveForApplication("tenant-1", null)).thenReturn("profile-1");
        var request = new ApplicationDtos.CreateLoanRequestApplicationRequest("Mary", null, "Wanjiku",
                "0700000000", "12345678", ApplicantIdType.NATIONAL_ID, null, null, null,
                new BigDecimal("20000"), 4);
        when(variables.prepare("tenant-1", "profile-1", List.of())).thenThrow(new BadRequestException("Required question missing"));
        assertThrows(BadRequestException.class, () -> service.create("tenant-1", "officer", request));
        verifyNoInteractions(applications, history, events, clients, fineract, otps);
        verify(variables, never()).savePrepared(anyString(), anyString(), anyList());
    }
}
