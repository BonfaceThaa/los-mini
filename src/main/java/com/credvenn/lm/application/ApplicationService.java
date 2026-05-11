package com.credvenn.lm.application;

import com.credvenn.lm.common.logging.LoggingContext;
import com.credvenn.lm.common.exception.BadRequestException;
import com.credvenn.lm.common.exception.NotFoundException;
import com.credvenn.lm.fineract.FineractDtos;
import com.credvenn.lm.fineract.FineractGateway;
import com.credvenn.lm.fineract.FineractLoanProduct;
import com.credvenn.lm.inventory.DepositType;
import com.credvenn.lm.inventory.InventoryDevice;
import com.credvenn.lm.inventory.InventoryDeviceAssignmentRepository;
import com.credvenn.lm.statement.StatementAnalysis;
import com.credvenn.lm.statement.StatementAnalysisRepository;
import com.credvenn.lm.statement.StatementAnalysisStatus;
import com.credvenn.lm.tenant.Tenant;
import com.credvenn.lm.tenant.TenantService;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationService.class);
    private static final MathContext PRICING_CONTEXT = new MathContext(16, RoundingMode.HALF_UP);

    private final LoanRequestApplicationRepository applicationRepository;
    private final ApplicationStatusHistoryRepository statusHistoryRepository;
    private final StatementAnalysisRepository statementAnalysisRepository;
    private final TenantService tenantService;
    private final FineractGateway fineractGateway;
    private final InventoryDeviceAssignmentRepository assignmentRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    public ApplicationService(
            LoanRequestApplicationRepository applicationRepository,
            ApplicationStatusHistoryRepository statusHistoryRepository,
            StatementAnalysisRepository statementAnalysisRepository,
            TenantService tenantService,
            FineractGateway fineractGateway,
            InventoryDeviceAssignmentRepository assignmentRepository,
            ApplicationEventPublisher applicationEventPublisher) {
        this.applicationRepository = applicationRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.statementAnalysisRepository = statementAnalysisRepository;
        this.tenantService = tenantService;
        this.fineractGateway = fineractGateway;
        this.assignmentRepository = assignmentRepository;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Transactional
    public ApplicationDtos.LoanRequestApplicationResponse create(
            String tenantId,
            String actor,
            ApplicationDtos.CreateLoanRequestApplicationRequest request) {
        log.info(
                "Creating loan application for tenantId={} applicant={} {} phone={} nationalId={} requestedAmount={} requestedTermMonths={}",
                tenantId,
                request.applicantFirstName().trim(),
                request.applicantLastName().trim(),
                LoggingContext.maskPhone(request.phoneNumber()),
                LoggingContext.maskNationalId(request.nationalId()),
                request.requestedAmount(),
                request.requestedTermMonths());
        LoanRequestApplication application = new LoanRequestApplication();
        application.setTenantId(tenantId);
        application.setApplicantFirstName(request.applicantFirstName().trim());
        application.setApplicantLastName(request.applicantLastName().trim());
        application.setPhoneNumber(request.phoneNumber().trim());
        application.setNationalId(request.nationalId().trim());
        application.setRequestedAmount(request.requestedAmount());
        application.setRequestedTermMonths(request.requestedTermMonths());
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
            if (assignmentRepository.findByApplicationId(applicationId).isEmpty()) {
                log.warn("Cannot activate Fineract loan because device assignment is missing");
                changeStatus(application, ApplicationStatus.DEVICE_ASSIGNMENT_FAILED, actor, "Device assignment required before activation");
                throw new BadRequestException("Device assignment is required before activation");
            }
            Tenant tenant = tenantService.getRequiredTenant(tenantId);
            log.info("Activating Fineract loan fineractLoanId={}", application.getFineractLoanId());
            fineractGateway.activateLoan(tenant, application);
            changeStatus(application, ApplicationStatus.FINERACT_LOAN_ACTIVATED, actor, "Fineract loan activated");
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
    public void handleKycPassed(String tenantId, String applicationId, String actor, String fineractClientId) {
        try (LoggingContext.Scope ignored = LoggingContext.withTenantAndApplication(tenantId, applicationId)) {
            LoanRequestApplication application = getRequired(tenantId, applicationId);
            application.setFineractClientId(fineractClientId);
            log.info("KYC passed and Fineract client created with fineractClientId={}", fineractClientId);
            changeStatus(application, ApplicationStatus.KYC_PASSED_CLIENT_CREATED, actor, "KYC passed and Fineract client created");
            changeStatus(application, ApplicationStatus.STATEMENT_PENDING, actor, "Awaiting statement upload");
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
            BigDecimal installmentAmount = calculateInstallmentAmount(principal, product.interestRatePerPeriod(), approvedTermMonths);
            BigDecimal totalRepayments = installmentAmount.multiply(BigDecimal.valueOf(approvedTermMonths)).setScale(2, RoundingMode.HALF_UP);
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
        Optional<StatementAnalysis> latestAnalysis = statementAnalysisRepository.findFirstByApplicationIdOrderByCreatedAtDesc(application.getId());
        if (latestAnalysis.isEmpty() || latestAnalysis.get().getStatus() != StatementAnalysisStatus.PASSED) {
            return List.of();
        }
        Tenant tenant = tenantService.getRequiredTenant(application.getTenantId());
        return fineractGateway.fetchActiveLoanProducts(tenant).stream()
                .filter(product -> product.maxPrincipal() == null || product.maxPrincipal().compareTo(application.getRequestedAmount()) >= 0)
                .filter(product -> product.minPrincipal() == null || product.minPrincipal().compareTo(application.getRequestedAmount()) <= 0)
                .filter(product -> product.maxTermMonths() == null || product.maxTermMonths() >= application.getRequestedTermMonths())
                .filter(product -> product.minTermMonths() == null || product.minTermMonths() <= application.getRequestedTermMonths())
                .toList();
    }

    private FineractLoanProduct requireActiveProduct(String tenantId, String productId) {
        Tenant tenant = tenantService.getRequiredTenant(tenantId);
        return fineractGateway.fetchActiveLoanProducts(tenant).stream()
                .filter(product -> product.id().equals(productId))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Loan product is not active for the tenant"));
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
    }

    static ApplicationDtos.LoanRequestApplicationResponse toResponse(
            LoanRequestApplication application,
            List<ApplicationStatusHistory> history) {
        return new ApplicationDtos.LoanRequestApplicationResponse(
                application.getId(),
                application.getTenantId(),
                application.getApplicantFirstName(),
                application.getApplicantLastName(),
                application.getPhoneNumber(),
                application.getNationalId(),
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

    private Integer resolveApprovedTermMonths(FineractLoanProduct product) {
        if (product.numberOfRepayments() != null && product.numberOfRepayments() > 0) {
            return product.numberOfRepayments();
        }
        if (product.minTermMonths() != null && product.maxTermMonths() != null && product.minTermMonths().equals(product.maxTermMonths())) {
            return product.minTermMonths();
        }
        throw new BadRequestException("Selected Fineract product does not expose a single supported term");
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

    private BigDecimal calculateInstallmentAmount(BigDecimal principal, BigDecimal interestRatePerPeriod, int numberOfRepayments) {
        if (numberOfRepayments <= 0) {
            throw new BadRequestException("Number of repayments must be greater than zero");
        }
        BigDecimal ratePercent = interestRatePerPeriod == null ? BigDecimal.ZERO : interestRatePerPeriod;
        if (ratePercent.signum() == 0) {
            return principal.divide(BigDecimal.valueOf(numberOfRepayments), 2, RoundingMode.HALF_UP);
        }
        double r = ratePercent.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP).doubleValue();
        double onePlusRPowerN = Math.pow(1 + r, numberOfRepayments);
        double numerator = principal.doubleValue() * (r * onePlusRPowerN);
        double denominator = onePlusRPowerN - 1;
        if (denominator == 0d) {
            throw new BadRequestException("Unable to calculate installment amount for the selected product");
        }
        return BigDecimal.valueOf(numerator / denominator).setScale(2, RoundingMode.HALF_UP);
    }
}
