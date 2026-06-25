package com.credvenn.lm.application;

import com.credvenn.lm.client.ClientRecordService;
import com.credvenn.lm.common.api.PagedResponse;
import com.credvenn.lm.common.api.PaginationSupport;
import com.credvenn.lm.common.exception.BadRequestException;
import com.credvenn.lm.common.exception.NotFoundException;
import com.credvenn.lm.common.logging.LoggingContext;
import com.credvenn.lm.payment.DepositPaymentRepository;
import com.credvenn.lm.payment.DepositPaymentStatus;
import com.credvenn.lm.fineract.FineractDtos;
import com.credvenn.lm.fineract.FineractGateway;
import com.credvenn.lm.fineract.FineractLoanProduct;
import com.credvenn.lm.inventory.DepositType;
import com.credvenn.lm.inventory.InventoryDevice;
import com.credvenn.lm.inventory.InventoryDeviceAssignmentRepository;
import com.credvenn.lm.loanproduct.LoanProductMapping;
import com.credvenn.lm.loanproduct.LoanProductMappingRepository;
import com.credvenn.lm.statement.StatementAnalysis;
import com.credvenn.lm.statement.StatementAnalysisRepository;
import com.credvenn.lm.statement.StatementAnalysisStatus;
import com.credvenn.lm.subscription.SubscriptionBillingService;
import com.credvenn.lm.subscription.SubscriptionGuardService;
import com.credvenn.lm.tenant.Tenant;
import com.credvenn.lm.tenant.TenantStatementAnalysisMode;
import com.credvenn.lm.tenant.TenantService;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationService.class);
    private static final MathContext PRICING_CONTEXT = new MathContext(16, RoundingMode.HALF_UP);
    private static final Map<String, String> APPLICATION_SORTS = new LinkedHashMap<>();

    static {
        APPLICATION_SORTS.put("createdAt", "createdAt");
        APPLICATION_SORTS.put("updatedAt", "updatedAt");
        APPLICATION_SORTS.put("applicantFirstName", "applicantFirstName");
        APPLICATION_SORTS.put("applicantLastName", "applicantLastName");
        APPLICATION_SORTS.put("requestedAmount", "requestedAmount");
        APPLICATION_SORTS.put("requestedTermMonths", "requestedTermMonths");
        APPLICATION_SORTS.put("status", "status");
    }

    private final LoanRequestApplicationRepository applicationRepository;
    private final ApplicationStatusHistoryRepository statusHistoryRepository;
    private final StatementAnalysisRepository statementAnalysisRepository;
    private final TenantService tenantService;
    private final FineractGateway fineractGateway;
    private final LoanProductMappingRepository loanProductMappingRepository;
    private final DepositPaymentRepository depositPaymentRepository;
    private final ClientRecordService clientRecordService;
    private final InventoryDeviceAssignmentRepository assignmentRepository;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final SubscriptionGuardService subscriptionGuardService;
    private final SubscriptionBillingService subscriptionBillingService;

    public ApplicationService(
            LoanRequestApplicationRepository applicationRepository,
            ApplicationStatusHistoryRepository statusHistoryRepository,
            StatementAnalysisRepository statementAnalysisRepository,
            TenantService tenantService,
            FineractGateway fineractGateway,
            LoanProductMappingRepository loanProductMappingRepository,
            DepositPaymentRepository depositPaymentRepository,
            ClientRecordService clientRecordService,
            InventoryDeviceAssignmentRepository assignmentRepository,
            ApplicationEventPublisher applicationEventPublisher,
            SubscriptionGuardService subscriptionGuardService,
            SubscriptionBillingService subscriptionBillingService) {
        this.applicationRepository = applicationRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.statementAnalysisRepository = statementAnalysisRepository;
        this.tenantService = tenantService;
        this.fineractGateway = fineractGateway;
        this.loanProductMappingRepository = loanProductMappingRepository;
        this.depositPaymentRepository = depositPaymentRepository;
        this.clientRecordService = clientRecordService;
        this.assignmentRepository = assignmentRepository;
        this.applicationEventPublisher = applicationEventPublisher;
        this.subscriptionGuardService = subscriptionGuardService;
        this.subscriptionBillingService = subscriptionBillingService;
    }

    @Transactional
    public ApplicationDtos.LoanRequestApplicationResponse create(
            String tenantId,
            String actor,
            ApplicationDtos.CreateLoanRequestApplicationRequest request) {
        subscriptionGuardService.assertCanCreateApplication(tenantId);
        log.info(
                "Creating loan application for tenantId={} applicant={} {} phone={} nationalId={} applicantIdType={} requestedAmount={} requestedTermMonths={}",
                tenantId,
                request.applicantFirstName().trim(),
                request.applicantLastName().trim(),
                LoggingContext.maskPhone(request.phoneNumber()),
                LoggingContext.maskNationalId(request.nationalId()),
                request.applicantIdType(),
                request.requestedAmount(),
                request.requestedTermMonths());
        LoanRequestApplication application = new LoanRequestApplication();
        application.setTenantId(tenantId);
        application.setApplicantFirstName(request.applicantFirstName().trim());
        application.setApplicantMiddleName(trimToNull(request.applicantMiddleName()));
        application.setApplicantLastName(request.applicantLastName().trim());
        application.setPhoneNumber(request.phoneNumber().trim());
        application.setNationalId(request.nationalId().trim());
        application.setApplicantIdType(request.applicantIdType());
        application.setDob(request.dob());
        application.setGender(trimToNull(request.gender()));
        application.setStatementOtp(trimToNull(request.statementOtp()));
        application.setRequestedAmount(request.requestedAmount());
        application.setRequestedTermMonths(request.requestedTermMonths());
        var existingClient = clientRecordService.findByTenantIdAndNationalId(tenantId, request.nationalId());
        if (existingClient != null) {
            application.setFineractClientId(existingClient.getFineractClientId());
            log.info("Matched existing client record by nationalId and reusing fineractClientId={}", existingClient.getFineractClientId());
        }
        application.setStatus(ApplicationStatus.SUBMITTED);
        application = applicationRepository.save(application);
        try (LoggingContext.Scope ignored = LoggingContext.withTenantAndApplication(tenantId, application.getId())) {
            log.info("Loan application persisted and ready for workflow processing");
            changeStatus(application, ApplicationStatus.PENDING_KYC, actor, "Application submitted");
            log.info("Publishing application-created event for background KYC startup");
            applicationEventPublisher.publishEvent(new ApplicationCreatedEvent(tenantId, application.getId(), actor));
            return get(tenantId, application.getId());
        }
    }

    @Transactional(readOnly = true)
    public PagedResponse<ApplicationDtos.LoanRequestApplicationResponse> list(
            String tenantId,
            Integer page,
            Integer size,
            String sortBy,
            String sortDir) {
        Pageable pageable = PaginationSupport.pageable(page, size, sortBy, sortDir, APPLICATION_SORTS, "createdAt");
        String normalizedSortBy = PaginationSupport.normalizeSortBy(sortBy, APPLICATION_SORTS, "createdAt");
        String normalizedSortDir = PaginationSupport.normalizeDirectionValue(sortDir);
        var applicationPage = applicationRepository.findAllByTenantId(tenantId, pageable)
                .map(application -> toResponse(application, statusHistoryRepository.findAllByApplicationIdOrderByIdAsc(application.getId())));
        return PagedResponse.fromPage(applicationPage, normalizedSortBy, normalizedSortDir);
    }

    @Transactional(readOnly = true)
    public List<ApplicationDtos.LoanRequestApplicationResponse> list(String tenantId) {
        return applicationRepository.findAllByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(application -> toResponse(application, statusHistoryRepository.findAllByApplicationIdOrderByIdAsc(application.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ApplicationDtos.LoanRequestApplicationResponse get(String tenantId, String applicationId) {
        LoanRequestApplication application = getRequired(tenantId, applicationId);
        return toResponse(application, statusHistoryRepository.findAllByApplicationIdOrderByIdAsc(applicationId));
    }

    @Transactional
    public ApplicationDtos.LoanRequestApplicationResponse captureConsent(
            String tenantId,
            String applicationId,
            String actor,
            ApplicationDtos.CaptureConsentRequest request) {
        try (LoggingContext.Scope ignored = LoggingContext.withTenantAndApplication(tenantId, applicationId)) {
            LoanRequestApplication application = getRequired(tenantId, applicationId);
            if (!request.accepted()) {
                throw new BadRequestException("Consent must be accepted before internal approval");
            }
            application.setConsentCaptured(true);
            application.setConsentCapturedBy(actor);
            application.setConsentCapturedAt(Instant.now());
            application.setConsentTextVersion(request.consentTextVersion().trim());
            log.info("Capturing consent for application with consentTextVersion={}", request.consentTextVersion().trim());
            changeStatus(application, ApplicationStatus.CONSENT_CAPTURED, actor, "Consent captured");
            return get(tenantId, applicationId);
        }
    }

    @Transactional
    public ApplicationDtos.LoanRequestApplicationResponse selectOffer(
            String tenantId,
            String applicationId,
            String actor,
            ApplicationDtos.SelectOfferRequest request) {
        try (LoggingContext.Scope ignored = LoggingContext.withTenantAndApplication(tenantId, applicationId)) {
            LoanRequestApplication application = getRequired(tenantId, applicationId);
            List<FineractLoanProduct> eligibleProducts = getEligibleProductsInternal(application);
            FineractLoanProduct product = eligibleProducts.stream()
                    .filter(item -> item.id().equals(request.fineractProductId()))
                    .findFirst()
                    .orElseThrow(() -> new BadRequestException("Selected product is not in the eligible list"));
            application.setSelectedFineractProductId(product.id());
            application.setSelectedFineractProductName(product.name());
            application.setSelectedOfferAt(Instant.now());
            log.info("Selected eligible offer fineractProductId={} fineractProductName={}", product.id(), product.name());
            changeStatus(application, ApplicationStatus.OFFER_SELECTED, actor, "Offer selected");
            return get(tenantId, applicationId);
        }
    }

    @Transactional(readOnly = true)
    public List<FineractDtos.LoanProductResponse> getEligibleProducts(String tenantId, String applicationId) {
        LoanRequestApplication application = getRequired(tenantId, applicationId);
        return getEligibleProductsInternal(application).stream().map(FineractDtos.LoanProductResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<FineractDtos.LoanProductResponse> getAllActiveProducts(String tenantId, String applicationId) {
        LoanRequestApplication application = getRequired(tenantId, applicationId);
        Tenant tenant = tenantService.getRequiredTenant(application.getTenantId());
        return fineractGateway.fetchActiveLoanProducts(tenant).stream().map(FineractDtos.LoanProductResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public FineractDtos.LoanRepaymentListResponse getLoanRepayments(String tenantId, String applicationId) {
        LoanRequestApplication application = getRequired(tenantId, applicationId);
        if (application.getFineractLoanId() == null || application.getFineractLoanId().isBlank()) {
            throw new BadRequestException("Fineract loan has not been created for this application");
        }
        Tenant tenant = tenantService.getRequiredTenant(application.getTenantId());
        return new FineractDtos.LoanRepaymentListResponse(
                fineractGateway.fetchLoanRepayments(tenant, application.getFineractLoanId()).stream()
                        .map(FineractDtos.LoanRepaymentResponse::from)
                        .toList());
    }

    @Transactional
    public ApplicationDtos.LoanRequestApplicationResponse internalApprove(
            String tenantId,
            String applicationId,
            String actor,
            ApplicationDtos.InternalApprovalRequest request) {
        try (LoggingContext.Scope ignored = LoggingContext.withTenantAndApplication(tenantId, applicationId)) {
            LoanRequestApplication application = getRequired(tenantId, applicationId);
            if (!application.isConsentCaptured()) {
                throw new BadRequestException("Consent must be captured before internal approval");
            }
            if (application.getFineractClientId() == null || application.getFineractClientId().isBlank()) {
                throw new BadRequestException("Fineract client must exist before internal approval");
            }
            if (assignmentRepository.findByApplicationId(applicationId).isEmpty()) {
                throw new BadRequestException("A device must be assigned before internal approval");
            }
            if (application.getApprovedAmount() == null || application.getApprovedTermMonths() == null) {
                throw new BadRequestException("Application pricing has not been computed from the assigned device");
            }
            String productId = application.getApprovedFineractProductId();
            if (productId == null || productId.isBlank()) {
                throw new BadRequestException("A Fineract product must be selected before internal approval");
            }
            FineractLoanProduct product = requireActiveProduct(tenantId, productId);
            application.setInternalApproved(true);
            application.setApprovedBy(actor);
            application.setApprovedAt(Instant.now());
            application.setApprovalReason(request.reason().trim());
            application.setApprovedFineractProductId(product.id());
            application.setApprovedFineractProductName(product.name());
            log.info(
                    "Recording internal approval with fineractProductId={} approvedAmount={} approvedTermMonths={}",
                    product.id(),
                    application.getApprovedAmount(),
                    application.getApprovedTermMonths());
            changeStatus(application, ApplicationStatus.INTERNAL_APPROVED, actor, "Internal approval completed");

            Tenant tenant = tenantService.getRequiredTenant(tenantId);
            String fineractLoanId = fineractGateway.createPendingLoan(
                    tenant,
                    application,
                    product,
                    application.getApprovedAmount(),
                    application.getApprovedTermMonths());
            application.setFineractLoanId(fineractLoanId);
            log.info("Pending Fineract loan created with fineractLoanId={}", fineractLoanId);
            changeStatus(application, ApplicationStatus.FINERACT_LOAN_CREATED_PENDING_DEVICE, actor, "Pending Fineract loan created");
            return get(tenantId, applicationId);
        }
    }

    @Transactional
    public ApplicationDtos.LoanRequestApplicationResponse activateLoan(String tenantId, String applicationId, String actor) {
        try (LoggingContext.Scope ignored = LoggingContext.withTenantAndApplication(tenantId, applicationId)) {
            LoanRequestApplication application = getRequired(tenantId, applicationId);
            if (application.getFineractLoanId() == null || application.getFineractLoanId().isBlank()) {
                throw new BadRequestException("Pending Fineract loan has not been created");
            }
            if (!depositPaymentRepository.existsByTenantIdAndMatchedApplicationIdAndStatusIn(
                    tenantId,
                    applicationId,
                    List.of(DepositPaymentStatus.MATCHED))) {
                throw new BadRequestException("A matched deposit payment is required before final loan approval");
            }
            if (assignmentRepository.findByApplicationId(applicationId).isEmpty()) {
                log.warn("Cannot activate Fineract loan because device assignment is missing");
                changeStatus(application, ApplicationStatus.DEVICE_ASSIGNMENT_FAILED, actor, "Device assignment required before activation");
                throw new BadRequestException("Device assignment is required before activation");
            }
            Tenant tenant = tenantService.getRequiredTenant(tenantId);
            log.info("Activating Fineract loan fineractLoanId={}", application.getFineractLoanId());
            fineractGateway.activateLoan(tenant, application);
            changeStatus(application, ApplicationStatus.FINERACT_LOAN_ACTIVATED, actor, "Fineract loan activated");
            subscriptionBillingService.evaluateNextCyclePricingMode(tenantId);
            return get(tenantId, applicationId);
        }
    }

    @Transactional
    public void markKycInProgress(String tenantId, String applicationId, String actor) {
        try (LoggingContext.Scope ignored = LoggingContext.withTenantAndApplication(tenantId, applicationId)) {
            LoanRequestApplication application = getRequired(tenantId, applicationId);
            changeStatus(application, ApplicationStatus.KYC_IN_PROGRESS, actor, "KYC processing started");
        }
    }

    @Transactional
    public void markKycPassed(String tenantId, String applicationId, String actor, String reason) {
        try (LoggingContext.Scope ignored = LoggingContext.withTenantAndApplication(tenantId, applicationId)) {
            LoanRequestApplication application = getRequired(tenantId, applicationId);
            log.info("KYC passed and is ready for separate Fineract client provisioning");
            changeStatus(application, ApplicationStatus.KYC_PASSED, actor, reason);
        }
    }

    @Transactional
    public void markClientCreationInProgress(String tenantId, String applicationId, String actor) {
        try (LoggingContext.Scope ignored = LoggingContext.withTenantAndApplication(tenantId, applicationId)) {
            LoanRequestApplication application = getRequired(tenantId, applicationId);
            changeStatus(application, ApplicationStatus.CLIENT_CREATION_IN_PROGRESS, actor, "Fineract client creation started");
        }
    }

    @Transactional
    public void handleClientCreated(String tenantId, String applicationId, String actor, String fineractClientId) {
        try (LoggingContext.Scope ignored = LoggingContext.withTenantAndApplication(tenantId, applicationId)) {
            LoanRequestApplication application = getRequired(tenantId, applicationId);
            application.setFineractClientId(fineractClientId);
            Tenant tenant = tenantService.getRequiredTenant(tenantId);
            clientRecordService.upsertFromFineract(
                    tenantId,
                    applicationId,
                    application.getNationalId(),
                    fineractGateway.fetchClient(tenant, fineractClientId));
            log.info("Fineract client created after KYC pass with fineractClientId={}", fineractClientId);
            changeStatus(application, ApplicationStatus.KYC_PASSED_CLIENT_CREATED, actor, "Fineract client created after KYC pass");
            if (tenant.getStatementAnalysisMode() == TenantStatementAnalysisMode.DISABLED) {
                recordDisabledStatementAnalysis(application, actor);
                changeStatus(application, ApplicationStatus.OFFERS_READY, actor, "Statement analysis disabled for tenant");
                return;
            }
            changeStatus(application, ApplicationStatus.STATEMENT_PENDING, actor, "Awaiting statement upload");
        }
    }

    @Transactional
    public void handleClientCreationFailed(String tenantId, String applicationId, String actor, String reason) {
        try (LoggingContext.Scope ignored = LoggingContext.withTenantAndApplication(tenantId, applicationId)) {
            LoanRequestApplication application = getRequired(tenantId, applicationId);
            log.warn("Fineract client creation failed after KYC pass: {}", reason);
            changeStatus(application, ApplicationStatus.CLIENT_CREATION_FAILED, actor, reason);
        }
    }

    @Transactional
    public void handleKycManualReview(String tenantId, String applicationId, String actor, String reason) {
        try (LoggingContext.Scope ignored = LoggingContext.withTenantAndApplication(tenantId, applicationId)) {
            LoanRequestApplication application = getRequired(tenantId, applicationId);
            log.warn("KYC moved to manual review: {}", reason);
            changeStatus(application, ApplicationStatus.KYC_MANUAL_REVIEW, actor, reason);
        }
    }

    @Transactional
    public void handleKycFailed(String tenantId, String applicationId, String actor, String reason) {
        try (LoggingContext.Scope ignored = LoggingContext.withTenantAndApplication(tenantId, applicationId)) {
            LoanRequestApplication application = getRequired(tenantId, applicationId);
            log.warn("KYC failed: {}", reason);
            changeStatus(application, ApplicationStatus.KYC_FAILED, actor, reason);
        }
    }

    @Transactional
    public void handleStatementInProgress(String tenantId, String applicationId, String actor) {
        try (LoggingContext.Scope ignored = LoggingContext.withTenantAndApplication(tenantId, applicationId)) {
            LoanRequestApplication application = getRequired(tenantId, applicationId);
            changeStatus(application, ApplicationStatus.STATEMENT_IN_PROGRESS, actor, "Statement analysis started");
        }
    }

    @Transactional
    public void handleStatementPassed(String tenantId, String applicationId, String actor) {
        try (LoggingContext.Scope ignored = LoggingContext.withTenantAndApplication(tenantId, applicationId)) {
            LoanRequestApplication application = getRequired(tenantId, applicationId);
            log.info("Statement analysis passed and eligible offers are ready");
            changeStatus(application, ApplicationStatus.STATEMENT_VERIFIED, actor, "Statement analysis passed");
            changeStatus(application, ApplicationStatus.OFFERS_READY, actor, "Eligible products ready");
        }
    }

    @Transactional
    public void handleStatementManualReview(String tenantId, String applicationId, String actor, String reason) {
        try (LoggingContext.Scope ignored = LoggingContext.withTenantAndApplication(tenantId, applicationId)) {
            LoanRequestApplication application = getRequired(tenantId, applicationId);
            log.warn("Statement analysis moved to manual review: {}", reason);
            changeStatus(application, ApplicationStatus.STATEMENT_MANUAL_REVIEW, actor, reason);
        }
    }

    @Transactional
    public void handleStatementFailed(String tenantId, String applicationId, String actor, String reason) {
        try (LoggingContext.Scope ignored = LoggingContext.withTenantAndApplication(tenantId, applicationId)) {
            LoanRequestApplication application = getRequired(tenantId, applicationId);
            log.warn("Statement analysis failed: {}", reason);
            changeStatus(application, ApplicationStatus.STATEMENT_FAILED, actor, reason);
        }
    }

    @Transactional
    public void handleDeviceAssigned(String tenantId, String applicationId, String actor, InventoryDevice device) {
        try (LoggingContext.Scope ignored = LoggingContext.withTenantAndApplication(tenantId, applicationId)) {
            LoanRequestApplication application = getRequired(tenantId, applicationId);
            if (application.getSelectedFineractProductId() == null || application.getSelectedFineractProductId().isBlank()) {
                throw new BadRequestException("A Fineract product must be selected before device assignment");
            }
            FineractLoanProduct product = requireActiveProduct(tenantId, application.getSelectedFineractProductId());
            Integer approvedTermMonths = resolveApprovedTermMonths(product);
            int repaymentCount = resolveRepaymentCount(product, approvedTermMonths);
            BigDecimal cashPrice = requiredPositive(device.getCashPrice(), "Assigned device cash price must be configured");
            BigDecimal depositValue = requiredPositive(device.getDepositValue(), "Assigned device deposit value must be configured");
            if (device.getDepositType() == null) {
                throw new BadRequestException("Assigned device deposit type must be configured");
            }
            BigDecimal depositAmount = calculateDepositAmount(cashPrice, device.getDepositType(), depositValue);
            BigDecimal principal = cashPrice.subtract(depositAmount).setScale(2, RoundingMode.HALF_UP);
            if (principal.signum() <= 0) {
                throw new BadRequestException("Computed principal must be greater than zero");
            }
            BigDecimal installmentAmount = calculateInstallmentAmount(
                    principal,
                    product.interestRatePerPeriod(),
                    product.interestRateFrequencyType(),
                    approvedTermMonths,
                    repaymentCount);
            BigDecimal totalRepayments = installmentAmount.multiply(BigDecimal.valueOf(repaymentCount)).setScale(2, RoundingMode.HALF_UP);
            BigDecimal totalPayment = depositAmount.add(totalRepayments).setScale(2, RoundingMode.HALF_UP);
            BigDecimal marginAmount = totalPayment.subtract(cashPrice).setScale(2, RoundingMode.HALF_UP);

            application.setAssignedDeviceId(device.getId());
            application.setAssignedDeviceName(device.getDeviceName());
            application.setAssignedDeviceImei1(device.getImei1());
            application.setAssignedDeviceImei2(device.getImei2());
            application.setAssignedDeviceCashPrice(cashPrice);
            application.setDepositType(device.getDepositType());
            application.setDepositValue(depositValue);
            application.setDepositAmount(depositAmount);
            application.setApprovedAmount(principal);
            application.setApprovedTermMonths(approvedTermMonths);
            application.setApprovedFineractProductId(product.id());
            application.setApprovedFineractProductName(product.name());
            application.setInstallmentAmount(installmentAmount);
            application.setTotalRepayments(totalRepayments);
            application.setTotalPayment(totalPayment);
            application.setMarginAmount(marginAmount);
            log.info(
                    "Device assignment computed pricing deviceId={} productId={} principal={} approvedTermMonths={} depositAmount={} installmentAmount={}",
                    device.getId(),
                    product.id(),
                    principal,
                    approvedTermMonths,
                    depositAmount,
                    installmentAmount);
            changeStatus(application, ApplicationStatus.DEVICE_ASSIGNED, actor, "Device assigned");
        }
    }

    @Transactional(readOnly = true)
    public LoanRequestApplication getRequired(String tenantId, String applicationId) {
        return applicationRepository.findByIdAndTenantId(applicationId, tenantId)
                .orElseThrow(() -> new NotFoundException("Loan request application not found"));
    }

    private List<FineractLoanProduct> getEligibleProductsInternal(LoanRequestApplication application) {
        Tenant tenant = tenantService.getRequiredTenant(application.getTenantId());
        if (tenant.getStatementAnalysisMode() != TenantStatementAnalysisMode.DISABLED) {
            Optional<StatementAnalysis> latestAnalysis = statementAnalysisRepository.findFirstByApplicationIdOrderByCreatedAtDesc(application.getId());
            if (latestAnalysis.isEmpty() || latestAnalysis.get().getStatus() != StatementAnalysisStatus.PASSED) {
                return List.of();
            }
        }
        return getActiveTenantProducts(application.getTenantId()).stream()
                .filter(product -> product.maxPrincipal() == null || product.maxPrincipal().compareTo(application.getRequestedAmount()) >= 0)
                .filter(product -> product.minPrincipal() == null || product.minPrincipal().compareTo(application.getRequestedAmount()) <= 0)
                .toList();
    }

    private void recordDisabledStatementAnalysis(LoanRequestApplication application, String actor) {
        StatementAnalysis existing = statementAnalysisRepository.findFirstByApplicationIdOrderByCreatedAtDesc(application.getId()).orElse(null);
        if (existing != null && "DISABLED".equals(existing.getProvider())) {
            existing.setStatus(StatementAnalysisStatus.PASSED);
            existing.setProviderStatus("BYPASSED");
            existing.setRecommendation("BYPASS");
            existing.setSummary("Statement analysis disabled for tenant");
            existing.setSourceDocumentId(null);
            existing.setRawProviderResponse("Statement analysis bypassed because tenant statementAnalysisMode=DISABLED");
            return;
        }
        StatementAnalysis analysis = new StatementAnalysis();
        analysis.setTenantId(application.getTenantId());
        analysis.setApplicationId(application.getId());
        analysis.setProvider("DISABLED");
        analysis.setProviderStatus("BYPASSED");
        analysis.setStatus(StatementAnalysisStatus.PASSED);
        analysis.setSourceDocumentId(null);
        analysis.setRecommendation("BYPASS");
        analysis.setSummary("Statement analysis disabled for tenant");
        analysis.setRawProviderResponse("Statement analysis bypassed because tenant statementAnalysisMode=DISABLED");
        statementAnalysisRepository.save(analysis);
        log.info("Bypassed statement analysis because tenant statementAnalysisMode=DISABLED actor={}", actor);
    }

    private FineractLoanProduct requireActiveProduct(String tenantId, String productId) {
        return getActiveTenantProducts(tenantId).stream()
                .filter(product -> product.id().equals(productId))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Loan product is not active for the tenant"));
    }

    private List<FineractLoanProduct> getActiveTenantProducts(String tenantId) {
        return loanProductMappingRepository.findAllByTenantIdAndActiveTrueOrderByDisplayNameAsc(tenantId).stream()
                .map(this::toLoanProduct)
                .toList();
    }

    private FineractLoanProduct toLoanProduct(LoanProductMapping mapping) {
        return new FineractLoanProduct(
                String.valueOf(mapping.getFineractProductId()),
                mapping.getDisplayName(),
                mapping.getShortName(),
                mapping.getPrincipalMin(),
                mapping.getPrincipalMax(),
                mapping.getNumberOfRepayments(),
                mapping.getNumberOfRepayments(),
                null,
                interestTypeId(mapping.getInterestType()),
                interestCalculationPeriodTypeId(mapping.getInterestCalculationPeriodType()),
                mapping.getInterestRatePerPeriod(),
                amortizationTypeId(mapping.getAmortizationType()),
                interestRateFrequencyTypeId(mapping.getInterestRateFrequency()),
                mapping.getRepaymentEvery(),
                repaymentFrequencyTypeId(mapping.getRepaymentFrequency()),
                mapping.getNumberOfRepayments(),
                mapping.getCurrencyCode(),
                mapping.isActive());
    }

    private void changeStatus(LoanRequestApplication application, ApplicationStatus newStatus, String actor, String reason) {
        if (application.getStatus() == newStatus) {
            return;
        }
        ApplicationStatus previous = application.getStatus();
        application.setStatus(newStatus);
        log.info(
                "Application status transition from={} to={} actor={} reason={}",
                previous == null ? "null" : previous.name(),
                newStatus.name(),
                actor,
                reason);
        ApplicationStatusHistory history = new ApplicationStatusHistory();
        history.setApplicationId(application.getId());
        history.setFromStatus(previous == null ? null : previous.name());
        history.setToStatus(newStatus.name());
        history.setChangedBy(actor);
        history.setReason(reason);
        statusHistoryRepository.save(history);
        if (newStatus == ApplicationStatus.FINERACT_LOAN_ACTIVATED
                && application.getFineractLoanId() != null
                && !application.getFineractLoanId().isBlank()) {
            applicationEventPublisher.publishEvent(new LoanActivatedEvent(
                    application.getTenantId(),
                    application.getId(),
                    application.getFineractLoanId(),
                    actor));
        }
    }

    static ApplicationDtos.LoanRequestApplicationResponse toResponse(
            LoanRequestApplication application,
            List<ApplicationStatusHistory> history) {
        return new ApplicationDtos.LoanRequestApplicationResponse(
                application.getId(),
                application.getTenantId(),
                application.getApplicantFirstName(),
                application.getApplicantMiddleName(),
                application.getApplicantLastName(),
                application.getPhoneNumber(),
                application.getNationalId(),
                application.getApplicantIdType(),
                application.getDob(),
                application.getGender(),
                application.getStatementOtp(),
                application.getRequestedAmount(),
                application.getRequestedTermMonths(),
                application.getStatus(),
                application.getFineractClientId(),
                application.getFineractLoanId(),
                application.getSelectedFineractProductId(),
                application.getSelectedFineractProductName(),
                application.isConsentCaptured(),
                application.getConsentTextVersion(),
                application.isInternalApproved(),
                application.getApprovedBy(),
                application.getApprovedAt(),
                application.getApprovedFineractProductId(),
                application.getApprovedFineractProductName(),
                application.getApprovedAmount(),
                application.getApprovedTermMonths(),
                application.getAssignedDeviceId(),
                application.getAssignedDeviceName(),
                application.getAssignedDeviceImei1(),
                application.getAssignedDeviceImei2(),
                application.getAssignedDeviceCashPrice(),
                application.getDepositType(),
                application.getDepositValue(),
                application.getDepositAmount(),
                application.getInstallmentAmount(),
                application.getTotalRepayments(),
                application.getTotalPayment(),
                application.getMarginAmount(),
                application.getCreatedAt(),
                application.getUpdatedAt(),
                history.stream()
                        .map(item -> new ApplicationDtos.ApplicationStatusHistoryResponse(
                                item.getFromStatus(),
                                item.getToStatus(),
                                item.getChangedBy(),
                                item.getReason()))
                        .toList());
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Integer resolveApprovedTermMonths(FineractLoanProduct product) {
        if (product.numberOfRepayments() != null && product.numberOfRepayments() > 0) {
            int repaymentEvery = product.repaymentEvery() == null || product.repaymentEvery() <= 0 ? 1 : product.repaymentEvery();
            int repaymentFrequencyType = product.repaymentFrequencyType() == null ? 2 : product.repaymentFrequencyType();
            BigDecimal totalFrequencyUnits = BigDecimal.valueOf((long) product.numberOfRepayments() * repaymentEvery);
            return switch (repaymentFrequencyType) {
                case 0 -> totalFrequencyUnits
                        .divide(BigDecimal.valueOf(30), 0, RoundingMode.CEILING)
                        .max(BigDecimal.ONE)
                        .intValue();
                case 1 -> totalFrequencyUnits
                        .divide(BigDecimal.valueOf(4), 0, RoundingMode.CEILING)
                        .max(BigDecimal.ONE)
                        .intValue();
                case 2 -> totalFrequencyUnits.intValue();
                default -> totalFrequencyUnits.intValue();
            };
        }
        Integer derivedMinTermMonths = minTermMonths(product);
        Integer derivedMaxTermMonths = maxTermMonths(product);
        if (derivedMinTermMonths != null && derivedMaxTermMonths != null && derivedMinTermMonths.equals(derivedMaxTermMonths)) {
            return derivedMinTermMonths;
        }
        throw new BadRequestException("Selected Fineract product does not expose a single supported term");
    }

    private int resolveRepaymentCount(FineractLoanProduct product, Integer approvedTermMonths) {
        if (product.numberOfRepayments() != null && product.numberOfRepayments() > 0) {
            return product.numberOfRepayments();
        }
        if (approvedTermMonths != null && approvedTermMonths > 0) {
            return approvedTermMonths;
        }
        throw new BadRequestException("Number of repayments must be greater than zero");
    }

    private Integer minTermMonths(FineractLoanProduct product) {
        return toTermMonths(product.minNumberOfRepayments(), product.repaymentEvery(), product.repaymentFrequencyType());
    }

    private Integer maxTermMonths(FineractLoanProduct product) {
        return toTermMonths(product.maxNumberOfRepayments(), product.repaymentEvery(), product.repaymentFrequencyType());
    }

    private Integer toTermMonths(Integer repaymentCount, Integer repaymentEvery, Integer repaymentFrequencyType) {
        if (repaymentCount == null || repaymentCount <= 0) {
            return null;
        }
        int cadence = repaymentEvery == null || repaymentEvery <= 0 ? 1 : repaymentEvery;
        int frequencyType = repaymentFrequencyType == null ? 2 : repaymentFrequencyType;
        BigDecimal totalFrequencyUnits = BigDecimal.valueOf((long) repaymentCount * cadence);
        return switch (frequencyType) {
            case 0 -> totalFrequencyUnits.divide(BigDecimal.valueOf(30), 0, RoundingMode.CEILING).max(BigDecimal.ONE).intValue();
            case 1 -> totalFrequencyUnits.divide(BigDecimal.valueOf(4), 0, RoundingMode.CEILING).max(BigDecimal.ONE).intValue();
            case 2 -> totalFrequencyUnits.intValue();
            case 3 -> totalFrequencyUnits.multiply(BigDecimal.valueOf(12)).intValue();
            default -> totalFrequencyUnits.intValue();
        };
    }

    private Integer interestTypeId(String value) {
        return switch (normalize(value)) {
            case "DECLINING_BALANCE" -> 0;
            case "FLAT" -> 1;
            default -> null;
        };
    }

    private Integer interestCalculationPeriodTypeId(String value) {
        return switch (normalize(value)) {
            case "DAILY" -> 0;
            case "SAME_AS_REPAYMENT_PERIOD" -> 1;
            default -> null;
        };
    }

    private Integer amortizationTypeId(String value) {
        return switch (normalize(value)) {
            case "EQUAL_PRINCIPAL_PAYMENTS" -> 0;
            case "EQUAL_INSTALLMENTS" -> 1;
            default -> null;
        };
    }

    private Integer interestRateFrequencyTypeId(String value) {
        return switch (normalize(value)) {
            case "MONTHS" -> 2;
            case "YEARS" -> 3;
            default -> null;
        };
    }

    private Integer repaymentFrequencyTypeId(String value) {
        return switch (normalize(value)) {
            case "DAYS" -> 0;
            case "WEEKS" -> 1;
            case "MONTHS" -> 2;
            case "YEARS" -> 3;
            default -> null;
        };
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private BigDecimal requiredPositive(BigDecimal value, String message) {
        if (value == null || value.signum() <= 0) {
            throw new BadRequestException(message);
        }
        return value;
    }

    private BigDecimal calculateDepositAmount(BigDecimal cashPrice, DepositType depositType, BigDecimal depositValue) {
        BigDecimal depositAmount = switch (depositType) {
            case PERCENTAGE -> cashPrice.multiply(depositValue, PRICING_CONTEXT)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            case FIXED_AMOUNT -> depositValue.setScale(2, RoundingMode.HALF_UP);
        };
        if (depositAmount.compareTo(cashPrice) >= 0) {
            throw new BadRequestException("Deposit amount must be less than the device cash price");
        }
        return depositAmount;
    }

    private BigDecimal calculateInstallmentAmount(
            BigDecimal principal,
            BigDecimal interestRatePerPeriod,
            Integer interestRateFrequencyType,
            int termInMonths,
            int numberOfRepayments) {
        if (termInMonths <= 0) {
            throw new BadRequestException("Loan term must be greater than zero");
        }
        if (numberOfRepayments <= 0) {
            throw new BadRequestException("Number of repayments must be greater than zero");
        }
        BigDecimal ratePercent = interestRatePerPeriod == null ? BigDecimal.ZERO : interestRatePerPeriod;
        if (ratePercent.signum() == 0) {
            return principal.divide(BigDecimal.valueOf(numberOfRepayments), 2, RoundingMode.HALF_UP);
        }
        BigDecimal totalInterestMultiplier = switch (interestRateFrequencyType == null ? 2 : interestRateFrequencyType) {
            case 3 -> ratePercent
                    .multiply(BigDecimal.valueOf(termInMonths), PRICING_CONTEXT)
                    .divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP)
                    .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);
            case 2 -> ratePercent
                    .multiply(BigDecimal.valueOf(termInMonths), PRICING_CONTEXT)
                    .divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);
            default -> throw new BadRequestException("Unsupported interest rate frequency type for installment calculation");
        };
        BigDecimal totalRepaymentAmount = principal.multiply(BigDecimal.ONE.add(totalInterestMultiplier), PRICING_CONTEXT);
        return totalRepaymentAmount.divide(BigDecimal.valueOf(numberOfRepayments), 2, RoundingMode.HALF_UP);
    }
}
