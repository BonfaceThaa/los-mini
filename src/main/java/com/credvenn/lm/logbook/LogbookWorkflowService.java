package com.credvenn.lm.logbook;

import static com.credvenn.lm.origination.OriginationProfileDtos.*;
import static com.credvenn.lm.logbook.LogbookLoanOperation.*;
import com.credvenn.lm.application.*;
import com.credvenn.lm.common.exception.*;
import com.credvenn.lm.fineract.FineractLoanProduct;
import com.credvenn.lm.kyc.*;
import com.credvenn.lm.loanproduct.*;
import com.credvenn.lm.origination.OriginationProfileRepository;
import com.credvenn.lm.security.CurrentActorService;
import com.credvenn.lm.statement.*;
import com.credvenn.lm.tenant.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.validation.constraints.*;
import java.math.*;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class LogbookWorkflowService {
    private final LogbookWorkflowPolicy policy;
    private final LogbookService capabilities;
    private final LoanRequestApplicationRepository applications;
    private final OriginationProfileRepository profiles;
    private final LoanProductMappingRepository products;
    private final KycCheckRepository kyc;
    private final StatementReviewRepository reviews;
    private final StatementAnalysisRepository statements;
    private final TenantService tenants;
    private final LogbookLoanOperationRepository operations;
    private final ApplicationStatusHistoryRepository history;
    private final CurrentActorService actors;
    private final EntityManager entityManager;

    public record FinancingRequest(@NotNull @PositiveOrZero Long expectedApplicationVersion,
        @NotNull @DecimalMin("0.01") @Digits(integer=17,fraction=2) BigDecimal amount) {}
    public record FinancingResponse(long applicationVersion, BigDecimal approvedAmount, Integer termMonths,
        String productCode, StageReadiness approvalReadiness) {}
    public record StageReadiness(Stage stage, boolean ready, Map<String,Boolean> checks, List<String> missingRequirements) {}
    public record WorkflowResponse(long applicationVersion, Map<Stage,StageReadiness> stages, List<LogbookLoanOperation> operations) {}

    public boolean applies(LoanRequestApplication application) { return policy.applies(application); }

    @Transactional(readOnly=true)
    public StageReadiness evaluate(LoanRequestApplication app, Stage stage) {
        var required = policy.required(app).get(stage);
        var evidence = capabilities.readinessForWorkflow(app);
        var tenant = tenants.getRequiredTenant(app.getTenantId());
        var product = selected(app);
        boolean kycPassed = kyc.findFirstByTenantIdAndApplicationIdOrderByCreatedAtDesc(app.getTenantId(), app.getId())
            .map(k -> k.getStatus() == KycStatus.PASSED || k.getStatus() == KycStatus.MANUALLY_APPROVED).orElse(false);
        boolean statementPassed = tenant.getStatementAnalysisMode() == TenantStatementAnalysisMode.DISABLED
            || reviews.findFirstByTenantIdAndApplicationIdOrderByCreatedAtDesc(app.getTenantId(),app.getId())
                .map(r -> r.getDecision() == StatementReviewDecision.APPROVED)
                .orElseGet(() -> statements.findFirstByTenantIdAndApplicationIdOrderByCreatedAtDesc(app.getTenantId(),app.getId())
                    .map(s -> s.getStatus() == StatementAnalysisStatus.PASSED).orElse(false));
        boolean validProduct = product != null && product.isActive() && Objects.equals(product.getOriginationProfileId(),app.getOriginationProfileId())
            && Objects.equals(String.valueOf(product.getFineractProductId()),app.getSelectedFineractProductId()) && supports(product);
        BigDecimal amount = stage == Stage.OFFER_SELECTION || app.getApprovedAmount() == null ? app.getRequestedAmount() : app.getApprovedAmount();
        boolean within = amount != null && amount.signum() > 0 && evidence.maximumSecuredAmount() != null
            && amount.compareTo(evidence.maximumSecuredAmount()) <= 0;
        boolean financed = validProduct && app.getApprovedAmount() != null && app.getApprovedTermMonths() != null
            && app.getApprovedTermMonths().equals(termMonths(product)) && inBounds(product,app.getApprovedAmount())
            && app.getRequestedAmount() != null && app.getApprovedAmount().compareTo(app.getRequestedAmount()) <= 0
            && Objects.equals(app.getApprovedFineractProductId(),app.getSelectedFineractProductId());
        Map<String,Boolean> checks = new LinkedHashMap<>();
        for (Requirement requirement : required) {
            checks.put(requirement.name(), switch(requirement) {
                case KYC_APPROVED -> kycPassed;
                case CLIENT_PROVISIONED -> app.getFineractClientId() != null && !app.getFineractClientId().isBlank();
                case STATEMENT_ACCEPTED -> statementPassed;
                case CONSENT_CAPTURED -> app.isConsentCaptured();
                case VEHICLE_OWNERSHIP_VERIFIED -> evidence.ownershipVerified();
                case VALUATION_APPROVED -> evidence.valuationApproved();
                case SECURITY_REGISTRATION_CONFIRMED -> evidence.securityRegistrationConfirmed();
                case INSURANCE_VALID -> evidence.insuranceValid();
                case FINANCING_CALCULATED -> financed;
                case ACTIVE_PRODUCT_SELECTED -> validProduct && amount != null && inBounds(product,amount);
                case INTERNAL_APPROVAL_VALID -> app.isInternalApproved() && financed;
                case PENDING_LOAN_CREATED -> app.getFineractLoanId() != null && !app.getFineractLoanId().isBlank();
                default -> false;
            });
        }
        checks.put("AMOUNT_WITHIN_LTV",within);
        var missing = checks.entrySet().stream().filter(e -> !e.getValue()).map(Map.Entry::getKey).sorted().toList();
        return new StageReadiness(stage,missing.isEmpty(),checks,missing);
    }
    public void require(LoanRequestApplication app, Stage stage) {
        var result = evaluate(app,stage);
        if (!result.ready()) throw new BadRequestException("Logbook " + stage + " requires: " + String.join(", ",result.missingRequirements()));
    }

    @Transactional @PreAuthorize("hasAuthority('LOAN_CREATE')")
    public FinancingResponse assess(String applicationId, FinancingRequest request) {
        var actor=actors.requireCurrentUser(); var app=lock(actor.tenantId(),applicationId);
        if (request.expectedApplicationVersion() == null || request.expectedApplicationVersion() != app.getVersion())
            throw new ConflictException("Application changed; reload before assessing financing");
        if (app.isInternalApproved() || app.getFineractLoanId() != null) throw new ConflictException("Financing is locked after approval");
        var product=lockProduct(app);
        require(app,Stage.OFFER_SELECTION);
        if (request.amount() == null || request.amount().signum() <= 0 || request.amount().scale() > 2
            || !inBounds(product,request.amount()) || request.amount().compareTo(app.getRequestedAmount()) > 0)
            throw new BadRequestException("Financing amount must be positive, within product limits and no greater than the requested amount");
        app.setApprovedAmount(request.amount().setScale(2)); app.setApprovedTermMonths(termMonths(product));
        app.setApprovedFineractProductId(String.valueOf(product.getFineractProductId())); app.setApprovedFineractProductName(product.getDisplayName());
        // Fineract owns amortization and repayment schedules; do not reuse phone flat-interest estimates.
        app.setInstallmentAmount(null); app.setTotalRepayments(null); app.setTotalPayment(null);
        transition(app,ApplicationStatus.FINANCING_CALCULATED,actor.username(),"Logbook financing assessed: " + app.getApprovedAmount() + " KES, product " + product.getProductCode() + ", term " + app.getApprovedTermMonths() + " months");
        applications.flush();
        return new FinancingResponse(app.getVersion(),app.getApprovedAmount(),app.getApprovedTermMonths(),product.getProductCode(),evaluate(app,Stage.INTERNAL_APPROVAL));
    }

    @Transactional @PreAuthorize("hasAuthority('CREDIT_MANUAL_APPROVE')")
    public void approve(String tenantId,String applicationId,String actor,String reason) {
        var app=lock(tenantId,applicationId);
        if (operations.findByTenantIdAndApplicationIdAndKind(tenantId,applicationId,Kind.CREATE_LOAN).isPresent()) return;
        lockProduct(app); require(app,Stage.INTERNAL_APPROVAL);
        app.setInternalApproved(true);app.setApprovedBy(actor);app.setApprovedAt(Instant.now());app.setApprovalReason(reason.trim());
        enqueue(app,Kind.CREATE_LOAN,actor);
        transition(app,ApplicationStatus.LOAN_CREATION_QUEUED,actor,"Internal approval recorded; pending-loan creation queued");
    }

    @Transactional @PreAuthorize("hasAuthority('LOAN_CREATE')")
    public void disburse(String tenantId,String applicationId,String actor) {
        var app=lock(tenantId,applicationId);
        if (app.getStatus() == ApplicationStatus.FINERACT_LOAN_ACTIVATED) return;
        if (operations.findByTenantIdAndApplicationIdAndKind(tenantId,applicationId,Kind.DISBURSE).isPresent()) return;
        lockProduct(app); require(app,Stage.INTERNAL_APPROVAL); require(app,Stage.DISBURSEMENT);
        enqueue(app,Kind.DISBURSE,actor);
        transition(app,ApplicationStatus.DISBURSEMENT_QUEUED,actor,"Logbook approval and disbursement queued");
    }

    @Transactional(readOnly=true) @PreAuthorize("hasAuthority('LOAN_VIEW')")
    public WorkflowResponse get(String applicationId) {
        var actor=actors.requireCurrentUser();var app=applications.findByIdAndTenantId(applicationId,actor.tenantId())
            .orElseThrow(()->new NotFoundException("Application not found"));
        Map<Stage,StageReadiness> stages=new EnumMap<>(Stage.class);
        for(var stage:Stage.values()) stages.put(stage,evaluate(app,stage));
        return new WorkflowResponse(app.getVersion(),stages,operations.findAllByTenantIdAndApplicationIdOrderByCreatedAtAsc(actor.tenantId(),applicationId));
    }

    @Transactional @PreAuthorize("hasAuthority('CREDIT_MANUAL_APPROVE')")
    public LogbookLoanOperation retry(String applicationId,String operationId) {
        var actor=actors.requireCurrentUser();var app=lock(actor.tenantId(),applicationId);
        var op=operations.findForUpdateByTenantIdAndApplicationIdAndId(actor.tenantId(),applicationId,operationId)
            .orElseThrow(()->new NotFoundException("Operation not found"));
        if(op.getState()==State.SUCCEEDED || op.getState()==State.QUEUED) return op;
        if(op.getState()==State.RUNNING && (op.getStartedAt()==null || op.getStartedAt().isAfter(Instant.now().minusSeconds(900))))
            throw new ConflictException("Operation is still running");
        // Retrying dispatch always reconciles remote state first, never blindly recreates a loan.
        op.setState(State.QUEUED);op.setLastError(null);op.setCompletedAt(null);op.setRequestedBy(actor.username());
        transition(app,op.getKind()==Kind.CREATE_LOAN?ApplicationStatus.LOAN_CREATION_QUEUED:ApplicationStatus.DISBURSEMENT_QUEUED,actor.username(),"Logbook operation retry queued");
        return op;
    }

    /** Called by application commands and the background worker within a transaction. */
    public LoanRequestApplication lock(String tenantId,String applicationId) {
        var app=applications.findForLogbookUpdateByTenantIdAndId(tenantId,applicationId).orElseThrow(()->new NotFoundException("Application not found"));
        entityManager.refresh(app,LockModeType.PESSIMISTIC_WRITE);
        var profile=profiles.findForApplicationByTenantIdAndId(tenantId,app.getOriginationProfileId()).orElseThrow(()->new NotFoundException("Profile not found"));
        entityManager.refresh(profile,LockModeType.PESSIMISTIC_READ);policy.required(app);
        if(app.getStatus()==ApplicationStatus.REJECTED || app.getStatus()==ApplicationStatus.LOAN_CLOSED)
            throw new ConflictException("Application is closed");
        return app;
    }
    public LoanProductMapping lockProduct(LoanRequestApplication app) {
        if(app.getSelectedLoanProductMappingId()==null) throw new BadRequestException("Select an eligible product first");
        var product=products.findForUpdateByTenantIdAndId(app.getTenantId(),app.getSelectedLoanProductMappingId())
            .orElseThrow(()->new BadRequestException("Selected product is unavailable"));
        entityManager.refresh(product,LockModeType.PESSIMISTIC_WRITE);
        if(!product.isActive() || !Objects.equals(product.getOriginationProfileId(),app.getOriginationProfileId())
            || !Objects.equals(String.valueOf(product.getFineractProductId()),app.getSelectedFineractProductId()) || !supports(product))
            throw new BadRequestException("Selected logbook product must be active, same-profile, KES and have a supported repayment term");
        return product;
    }
    private LoanProductMapping selected(LoanRequestApplication app) {
        return app.getSelectedLoanProductMappingId()==null?null:products.findByTenantIdAndId(app.getTenantId(),app.getSelectedLoanProductMappingId()).orElse(null);
    }
    public boolean supports(LoanProductMapping p) {
        return "KES".equalsIgnoreCase(p.getCurrencyCode()) && p.getNumberOfRepayments()!=null && p.getNumberOfRepayments()>0
            && p.getRepaymentEvery()!=null && p.getRepaymentEvery()>0 && "MONTHS".equalsIgnoreCase(p.getRepaymentFrequency())
            && (long)p.getNumberOfRepayments()*p.getRepaymentEvery()<=360;
    }
    public boolean inBounds(LoanProductMapping p,BigDecimal amount) {
        return amount!=null && amount.signum()>0 && (p.getPrincipalMin()==null || amount.compareTo(p.getPrincipalMin())>=0)
            && (p.getPrincipalMax()==null || amount.compareTo(p.getPrincipalMax())<=0);
    }
    private Integer termMonths(LoanProductMapping p) { return p==null || !supports(p)?null:p.getNumberOfRepayments()*p.getRepaymentEvery(); }
    private void enqueue(LoanRequestApplication app,Kind kind,String actor) {
        var op=new LogbookLoanOperation();op.setId(UUID.randomUUID().toString());op.setTenantId(app.getTenantId());op.setApplicationId(app.getId());
        op.setKind(kind);op.setState(State.QUEUED);op.setCreatedBy(actors.requireCurrentUser().userId());op.setCreatedAt(Instant.now());op.setRequestedBy(actor);operations.save(op);
    }
    public void transition(LoanRequestApplication app,ApplicationStatus status,String actor,String reason) {
        // Record repeated financing assessments too, so each approved-amount proposal remains auditable.
        var h=new ApplicationStatusHistory();h.setApplicationId(app.getId());h.setFromStatus(app.getStatus()==null?null:app.getStatus().name());
        h.setToStatus(status.name());h.setChangedBy(actor);h.setReason(reason);history.save(h);app.setStatus(status);
    }
    public FineractLoanProduct remoteProduct(LoanProductMapping p) {
        return new FineractLoanProduct(String.valueOf(p.getFineractProductId()),p.getDisplayName(),p.getShortName(),p.getPrincipalMin(),p.getPrincipalMax(),
            p.getNumberOfRepayments(),p.getNumberOfRepayments(),null,
            switch(p.getInterestType()){case "DECLINING_BALANCE"->0;case "FLAT"->1;default->throw new BadRequestException("Unsupported interest type");},
            switch(p.getInterestCalculationPeriodType()){case "DAILY"->0;case "SAME_AS_REPAYMENT_PERIOD"->1;default->throw new BadRequestException("Unsupported interest calculation period");},
            p.getInterestRatePerPeriod(),
            switch(p.getAmortizationType()){case "EQUAL_PRINCIPAL"->0;case "EQUAL_INSTALLMENTS"->1;default->throw new BadRequestException("Unsupported amortization");},
            switch(p.getInterestRateFrequency()){case "DAYS"->0;case "WEEKS"->1;case "MONTHS"->2;case "YEARS"->3;default->throw new BadRequestException("Unsupported rate frequency");},
            p.getRepaymentEvery(),2,p.getNumberOfRepayments(),p.getCurrencyCode(),p.isActive());
    }
}
