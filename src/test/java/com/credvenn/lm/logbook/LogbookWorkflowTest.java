package com.credvenn.lm.logbook;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.credvenn.lm.origination.OriginationProfileDtos.*;
import com.credvenn.lm.application.*;
import com.credvenn.lm.applicationvariable.ApplicationVariableService;
import com.credvenn.lm.client.ClientRecordService;
import com.credvenn.lm.common.exception.*;
import com.credvenn.lm.fineract.*;
import com.credvenn.lm.inventory.InventoryDeviceAssignmentRepository;
import com.credvenn.lm.kyc.*;
import com.credvenn.lm.loanproduct.*;
import com.credvenn.lm.origination.*;
import com.credvenn.lm.payment.DepositPaymentRepository;
import com.credvenn.lm.security.*;
import com.credvenn.lm.statement.*;
import com.credvenn.lm.subscription.*;
import com.credvenn.lm.tenant.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class LogbookWorkflowTest {
    private LoanRequestApplication app;
    private LoanProductMapping product;
    private OriginationProfile profile;
    private Tenant tenant;
    private LogbookWorkflowService workflow;
    private ApplicationService applicationService;
    private LogbookLoanProcessor processor;
    private LoanRequestApplicationRepository apps;
    private LoanProductMappingRepository products;
    private OriginationProfileRepository profiles;
    private KycCheckRepository kyc;
    private LogbookService capabilities;
    private FineractGateway gateway;
    private CurrentActorService actors;
    private LogbookLoanOperationRepository operations;
    private final Map<LogbookLoanOperation.Kind,LogbookLoanOperation> jobs=new EnumMap<>(LogbookLoanOperation.Kind.class);
    private DepositPaymentRepository deposits;
    private InventoryDeviceAssignmentRepository devices;
    private LogbookDtos.Readiness evidence;
    private SubscriptionBillingService billing;

    @BeforeEach void setup() throws Exception {
        apps=mock(LoanRequestApplicationRepository.class);products=mock(LoanProductMappingRepository.class);profiles=mock(OriginationProfileRepository.class);
        kyc=mock(KycCheckRepository.class);capabilities=mock(LogbookService.class);gateway=mock(FineractGateway.class);
        actors=mock(CurrentActorService.class);operations=mock(LogbookLoanOperationRepository.class);
        deposits=mock(DepositPaymentRepository.class);devices=mock(InventoryDeviceAssignmentRepository.class);billing=mock(SubscriptionBillingService.class);
        var json=new ObjectMapper().findAndRegisterModules();var entityManager=mock(EntityManager.class);var history=mock(ApplicationStatusHistoryRepository.class);
        var tenants=mock(TenantService.class);var reviews=mock(StatementReviewRepository.class);var statements=mock(StatementAnalysisRepository.class);
        var events=mock(ApplicationEventPublisher.class);
        app=new LoanRequestApplication();ReflectionTestUtils.setField(app,"id","app");app.setTenantId("a");app.setOriginationProfileId("profile");
        app.setRequestedAmount(new BigDecimal("480000"));app.setFineractClientId("3");app.setStatus(ApplicationStatus.SUBMITTED);
        when(apps.findByIdAndTenantId("app","a")).thenReturn(Optional.of(app));when(apps.findForLogbookUpdateByTenantIdAndId("a","app")).thenReturn(Optional.of(app));
        when(apps.findForLogbookUpdateByTenantIdAndId("b","app")).thenReturn(Optional.empty());
        doAnswer(c->{ReflectionTestUtils.setField(app,"version",app.getVersion()+1);return null;}).when(apps).flush();
        profile=new OriginationProfile();profile.setId("profile");profile.setTenantId("a");profile.setActive(true);
        profile.setRequirementsJson(json.writeValueAsString(OriginationProfileValidator.LOGBOOK));
        profile.setConfigurationJson("{\"valuationBasis\":\"FORCED_SALE_VALUE\",\"maxLtvRatio\":0.6,\"valuationValidityDays\":30}");
        when(profiles.findByTenantIdAndId("a","profile")).thenReturn(Optional.of(profile));when(profiles.findForApplicationByTenantIdAndId("a","profile")).thenReturn(Optional.of(profile));
        tenant=new Tenant();ReflectionTestUtils.setField(tenant,"id","a");tenant.setStatementAnalysisMode(TenantStatementAnalysisMode.DISABLED);
        when(tenants.getRequiredTenant("a")).thenReturn(tenant);
        var passed=new KycCheck();passed.setStatus(KycStatus.PASSED);when(kyc.findFirstByTenantIdAndApplicationIdOrderByCreatedAtDesc("a","app")).thenReturn(Optional.of(passed));
        product=new LoanProductMapping();ReflectionTestUtils.setField(product,"id","product");product.setTenantId("a");product.setOriginationProfileId("profile");
        product.setProductCode("LOGBOOK_12_MONTHS");product.setDisplayName("Logbook");product.setShortName("LB12");product.setFineractProductId(7L);product.setActive(true);
        product.setPrincipalMin(new BigDecimal("50000"));product.setPrincipalMax(new BigDecimal("2000000"));product.setNumberOfRepayments(12);product.setRepaymentEvery(1);
        product.setRepaymentFrequency("MONTHS");product.setCurrencyCode("KES");product.setInterestType("DECLINING_BALANCE");product.setInterestRateFrequency("MONTHS");
        product.setInterestCalculationPeriodType("SAME_AS_REPAYMENT_PERIOD");product.setInterestRatePerPeriod(new BigDecimal("2"));product.setAmortizationType("EQUAL_INSTALLMENTS");product.setTransactionProcessingStrategyCode("mifos-standard-strategy");
        when(products.findByTenantIdAndId("a","product")).thenReturn(Optional.of(product));when(products.findForUpdateByTenantIdAndId("a","product")).thenReturn(Optional.of(product));
        when(products.findAllByTenantIdAndOriginationProfileIdAndActiveTrueOrderByDisplayNameAsc("a","profile")).thenReturn(List.of(product));
        when(actors.requireCurrentUser()).thenReturn(new AuthenticatedUser("officer","a","officer","test@example.test",List.of(),List.of()));
        when(capabilities.readinessForWorkflow(any())).thenAnswer(c->evidence);evidence=evidence(true,true,false,false,new BigDecimal("480000"));
        when(operations.findByTenantIdAndApplicationIdAndKind(eq("a"),eq("app"),any())).thenAnswer(c->Optional.ofNullable(jobs.get(c.getArgument(2))));
        when(operations.save(any())).thenAnswer(c->{var op=(LogbookLoanOperation)c.getArgument(0);jobs.put(op.getKind(),op);return op;});
        when(operations.findForUpdateByTenantIdAndApplicationIdAndId(eq("a"),eq("app"),any())).thenAnswer(c->jobs.values().stream().filter(op->op.getId().equals(c.getArgument(2))).findFirst());
        when(operations.findAllByTenantIdAndApplicationIdOrderByCreatedAtAsc("a","app")).thenAnswer(c->List.copyOf(jobs.values()));
        var policy=new LogbookWorkflowPolicy(profiles,new OriginationProfileValidator(),json);
        workflow=new LogbookWorkflowService(policy,capabilities,apps,profiles,products,kyc,reviews,statements,tenants,operations,history,actors,entityManager);
        applicationService=new ApplicationService(apps,history,kyc,reviews,statements,tenants,gateway,products,deposits,mock(ClientRecordService.class),devices,
            mock(ApplicationStatementOtpService.class),events,mock(SubscriptionGuardService.class),billing,mock(ApplicationVariableService.class),mock(OriginationProfileResolver.class),entityManager,workflow);
        var transactions=mock(PlatformTransactionManager.class);when(transactions.getTransaction(any())).thenAnswer(c->new SimpleTransactionStatus());
        processor=new LogbookLoanProcessor(transactions,workflow,apps,operations,tenants,gateway,billing,events);
        when(gateway.findSecuredLoan(tenant,app)).thenReturn(Optional.empty());when(gateway.createSecuredPendingLoan(eq(tenant),eq(app),any(),anyString())).thenReturn("100");
    }
    private LogbookDtos.Readiness evidence(boolean owned,boolean valued,boolean insured,boolean secured,BigDecimal limit){
        return new LogbookDtos.Readiness(true,owned,valued,insured,secured,"KES",limit,limit!=null&&limit.compareTo(app.getRequestedAmount())>=0,List.of());
    }
    private void selectAndFinance(){
        applicationService.selectOffer("a","app","officer",new ApplicationDtos.SelectOfferRequest("LOGBOOK_12_MONTHS"));
        workflow.assess("app",new LogbookWorkflowService.FinancingRequest(app.getVersion(),new BigDecimal("480000")));
        applicationService.captureConsent("a","app","officer",new ApplicationDtos.CaptureConsentRequest(true,"v1"));
    }
    private void approve(){applicationService.internalApprove("a","app","officer",new ApplicationDtos.InternalApprovalRequest("Credit approved"));}
    private void run(LogbookLoanOperation.Kind kind){var op=jobs.get(kind);processor.process("a","app",op.getId());}
    @Test void completeJourneyUsesSameApplicationEndpointsWithoutDevicesOrDeposits(){
        assertTrue(applicationService.getEligibleProducts("a","app").offersReady());selectAndFinance();
        assertNull(app.getInstallmentAmount());assertEquals(12,app.getApprovedTermMonths());
        approve();assertEquals(ApplicationStatus.LOAN_CREATION_QUEUED,app.getStatus());verifyNoInteractions(gateway);
        approve();assertEquals(1,jobs.size());run(LogbookLoanOperation.Kind.CREATE_LOAN);
        assertEquals("100",app.getFineractLoanId());assertEquals(ApplicationStatus.FINERACT_LOAN_CREATED_PENDING_SECURITY,app.getStatus());
        assertThrows(BadRequestException.class,()->applicationService.activateLoan("a","app","officer"));
        evidence=evidence(true,true,true,true,new BigDecimal("480000"));
        when(gateway.findSecuredLoan(tenant,app)).thenReturn(Optional.of(new FineractGateway.LoanSummary("100",false,"loanStatusType.submitted.and.pending.approval")));
        applicationService.activateLoan("a","app","officer");assertEquals(ApplicationStatus.DISBURSEMENT_QUEUED,app.getStatus());
        verify(gateway,never()).activateLoan(any(),any());run(LogbookLoanOperation.Kind.DISBURSE);
        assertEquals(ApplicationStatus.FINERACT_LOAN_ACTIVATED,app.getStatus());verify(gateway,times(1)).activateLoan(tenant,app);
        applicationService.activateLoan("a","app","officer");run(LogbookLoanOperation.Kind.DISBURSE);
        verify(gateway,times(1)).activateLoan(tenant,app);verifyNoInteractions(devices,deposits);
    }
    @Test void expiredOrMissingEvidenceBlocksOffersAndApproval(){
        evidence=evidence(true,false,false,false,null);assertFalse(applicationService.getEligibleProducts("a","app").offersReady());
        assertTrue(applicationService.getEligibleProducts("a","app").products().isEmpty());
        evidence=evidence(true,true,false,false,new BigDecimal("480000"));selectAndFinance();evidence=evidence(false,true,false,false,new BigDecimal("480000"));
        assertThrows(BadRequestException.class,this::approve);assertTrue(jobs.isEmpty());verifyNoInteractions(gateway);
    }
    @Test void workerRechecksEvidenceAndBlockedDisbursementCanBeRenewed(){
        selectAndFinance();approve();run(LogbookLoanOperation.Kind.CREATE_LOAN);evidence=evidence(true,true,true,true,new BigDecimal("480000"));
        when(gateway.findSecuredLoan(tenant,app)).thenReturn(Optional.of(new FineractGateway.LoanSummary("100",false,"loanStatusType.approved")));
        applicationService.activateLoan("a","app","officer");evidence=evidence(true,true,false,true,new BigDecimal("480000"));run(LogbookLoanOperation.Kind.DISBURSE);
        var job=jobs.get(LogbookLoanOperation.Kind.DISBURSE);assertEquals(LogbookLoanOperation.State.BLOCKED,job.getState());verify(gateway,never()).activateLoan(any(),any());
        evidence=evidence(true,true,true,true,new BigDecimal("480000"));workflow.retry("app",job.getId());run(LogbookLoanOperation.Kind.DISBURSE);
        assertEquals(LogbookLoanOperation.State.SUCCEEDED,job.getState());
    }
    @Test void timeoutIsReconciledWithoutCreatingAnotherLoan(){
        selectAndFinance();approve();when(gateway.createSecuredPendingLoan(eq(tenant),eq(app),any(),anyString())).thenThrow(new RuntimeException("simulated timeout"));
        run(LogbookLoanOperation.Kind.CREATE_LOAN);var job=jobs.get(LogbookLoanOperation.Kind.CREATE_LOAN);
        assertEquals(LogbookLoanOperation.State.REVIEW_REQUIRED,job.getState());assertFalse(job.getLastError().contains("simulated"));
        when(gateway.findSecuredLoan(tenant,app)).thenReturn(Optional.of(new FineractGateway.LoanSummary("100",false,"loanStatusType.submitted.and.pending.approval")));
        workflow.retry("app",job.getId());run(LogbookLoanOperation.Kind.CREATE_LOAN);
        assertEquals("100",app.getFineractLoanId());assertEquals(LogbookLoanOperation.State.SUCCEEDED,job.getState());
        verify(gateway,times(1)).createSecuredPendingLoan(eq(tenant),eq(app),any(),anyString());
    }
    @Test void remoteActivationReconcilesEvenIfEvidenceHasSinceExpired(){
        selectAndFinance();approve();run(LogbookLoanOperation.Kind.CREATE_LOAN);evidence=evidence(true,true,true,true,new BigDecimal("480000"));
        applicationService.activateLoan("a","app","officer");evidence=evidence(true,false,false,true,null);
        when(gateway.findSecuredLoan(tenant,app)).thenReturn(Optional.of(new FineractGateway.LoanSummary("100",true,"loanStatusType.active")));
        run(LogbookLoanOperation.Kind.DISBURSE);assertEquals(ApplicationStatus.FINERACT_LOAN_ACTIVATED,app.getStatus());verify(gateway,never()).activateLoan(any(),any());
    }
    @Test void staleFinancingAndChangedProductsCannotBypassGates(){
        selectAndFinance();assertThrows(ConflictException.class,()->workflow.assess("app",new LogbookWorkflowService.FinancingRequest(0L,BigDecimal.TEN)));
        product.setActive(false);assertThrows(BadRequestException.class,this::approve);
        product.setActive(true);product.setOriginationProfileId("other");assertThrows(BadRequestException.class,this::approve);verifyNoInteractions(gateway);
    }
    @Test void ltvChangesAndKycRevocationAreRechecked(){
        selectAndFinance();evidence=evidence(true,true,true,true,new BigDecimal("479999"));assertThrows(BadRequestException.class,this::approve);
        evidence=evidence(true,true,true,true,new BigDecimal("480000"));when(kyc.findFirstByTenantIdAndApplicationIdOrderByCreatedAtDesc("a","app")).thenReturn(Optional.empty());
        assertThrows(BadRequestException.class,this::approve);
    }
    @Test void tenantBoundaryAndRunningOperationRetryAreProtected(){
        assertThrows(NotFoundException.class,()->workflow.lock("b","app"));selectAndFinance();approve();
        var job=jobs.get(LogbookLoanOperation.Kind.CREATE_LOAN);job.setState(LogbookLoanOperation.State.RUNNING);job.setStartedAt(Instant.now());
        assertThrows(ConflictException.class,()->workflow.retry("app",job.getId()));job.setStartedAt(Instant.now().minusSeconds(901));
        assertEquals(LogbookLoanOperation.State.QUEUED,workflow.retry("app",job.getId()).getState());
    }
    @Test void lateStatementCallbacksDoNotRegressSelectedLogbookStatus(){
        selectAndFinance();approve();applicationService.handleStatementPassed("a","app","system");
        assertEquals(ApplicationStatus.LOAN_CREATION_QUEUED,app.getStatus());
    }
    @Test void unsupportedCurrencyAndCadenceAreNotOffered(){
        product.setCurrencyCode("USD");assertTrue(applicationService.getEligibleProducts("a","app").products().isEmpty());
        product.setCurrencyCode("KES");product.setRepaymentFrequency("WEEKS");assertTrue(applicationService.getEligibleProducts("a","app").products().isEmpty());
    }
}
