package com.credvenn.lm.loanproduct;

import com.credvenn.lm.common.exception.BadRequestException;
import com.credvenn.lm.common.exception.ConflictException;
import com.credvenn.lm.common.exception.NotFoundException;
import com.credvenn.lm.common.api.PagedResponse;
import com.credvenn.lm.common.api.PaginationSupport;
import com.credvenn.lm.fineract.FineractDtos;
import com.credvenn.lm.fineract.FineractGateway;
import com.credvenn.lm.fineract.FineractGateway.CreateLoanProductRequest;
import com.credvenn.lm.fineract.FineractGateway.UpdateLoanProductRequest;
import com.credvenn.lm.fineracttemplate.GlAccountTemplate;
import com.credvenn.lm.fineracttemplate.GlAccountTemplateRepository;
import com.credvenn.lm.security.CurrentActorService;
import com.credvenn.lm.tenant.Tenant;
import com.credvenn.lm.tenant.TenantService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoanProductCatalogService {

    private static final Map<String, String> LOAN_PRODUCT_SORTS = new LinkedHashMap<>();

    static {
        LOAN_PRODUCT_SORTS.put("name", "displayName");
        LOAN_PRODUCT_SORTS.put("shortName", "shortName");
        LOAN_PRODUCT_SORTS.put("minPrincipal", "principalMin");
        LOAN_PRODUCT_SORTS.put("maxPrincipal", "principalMax");
        LOAN_PRODUCT_SORTS.put("minNumberOfRepayments", "numberOfRepayments");
        LOAN_PRODUCT_SORTS.put("maxNumberOfRepayments", "numberOfRepayments");
        LOAN_PRODUCT_SORTS.put("numberOfRepayments", "numberOfRepayments");
        LOAN_PRODUCT_SORTS.put("interestRatePerPeriod", "interestRatePerPeriod");
        LOAN_PRODUCT_SORTS.put("currencyCode", "currencyCode");
    }

    private final LoanProductMappingRepository loanProductMappingRepository;
    private final GlAccountTemplateRepository glAccountTemplateRepository;
    private final CurrentActorService currentActorService;
    private final TenantService tenantService;
    private final FineractGateway fineractGateway;

    public LoanProductCatalogService(
            LoanProductMappingRepository loanProductMappingRepository,
            GlAccountTemplateRepository glAccountTemplateRepository,
            CurrentActorService currentActorService,
            TenantService tenantService,
            FineractGateway fineractGateway) {
        this.loanProductMappingRepository = loanProductMappingRepository;
        this.glAccountTemplateRepository = glAccountTemplateRepository;
        this.currentActorService = currentActorService;
        this.tenantService = tenantService;
        this.fineractGateway = fineractGateway;
    }

    @Transactional
    public LoanProductCatalogDtos.LoanProductCatalogResponse createCurrentTenantProduct(LoanProductCatalogDtos.CreateLoanProductRequest request) {
        validateRequest(request);
        var actor = currentActorService.requireCurrentUser();
        String tenantId = actor.tenantId();
        String normalizedProductCode = normalize(request.productCode());
        if (loanProductMappingRepository.existsByTenantIdAndProductCodeIgnoreCase(tenantId, normalizedProductCode)) {
            throw new ConflictException("Loan product code already exists for this tenant");
        }

        Tenant tenant = tenantService.getRequiredTenant(tenantId);
        ProductAccounts productAccounts = resolveAccounts(request.accountingAccounts());
        String fineractProductId = fineractGateway.createLoanProduct(tenant, new CreateLoanProductRequest(
                request.displayName().trim(),
                request.shortName().trim().toUpperCase(),
                trimToNull(request.description()),
                request.currencyCode().trim().toUpperCase(),
                request.principal().defaultAmount().setScale(2, RoundingMode.HALF_UP),
                request.principal().min().setScale(2, RoundingMode.HALF_UP),
                request.principal().max().setScale(2, RoundingMode.HALF_UP),
                request.term().numberOfRepayments(),
                request.term().repaymentEvery(),
                repaymentFrequencyType(request.term().repaymentFrequency()),
                request.interest().ratePerPeriod().setScale(4, RoundingMode.HALF_UP),
                interestRateFrequencyType(request.interest().rateFrequency()),
                interestType(request.interest().interestType()),
                interestCalculationPeriodType(request.interest().calculationPeriodType()),
                amortizationType(request.amortizationType()),
                trimToNull(request.transactionProcessingStrategyCode()),
                productAccounts.loanPortfolioAccountId(),
                productAccounts.fundSourceAccountId(),
                productAccounts.interestOnLoanAccountId(),
                productAccounts.incomeFromFeeAccountId(),
                productAccounts.incomeFromPenaltyAccountId(),
                productAccounts.incomeFromRecoveryAccountId(),
                productAccounts.writeOffAccountId(),
                productAccounts.transfersInSuspenseAccountId(),
                productAccounts.overpaymentLiabilityAccountId()));

        LoanProductMapping mapping = new LoanProductMapping();
        mapping.setTenantId(tenantId);
        mapping.setProductCode(normalizedProductCode);
        mapping.setDisplayName(request.displayName().trim());
        mapping.setShortName(request.shortName().trim().toUpperCase());
        mapping.setDescription(trimToNull(request.description()));
        mapping.setCurrencyCode(request.currencyCode().trim().toUpperCase());
        mapping.setPrincipalMin(request.principal().min().setScale(2, RoundingMode.HALF_UP));
        mapping.setPrincipalDefaultAmount(request.principal().defaultAmount().setScale(2, RoundingMode.HALF_UP));
        mapping.setPrincipalMax(request.principal().max().setScale(2, RoundingMode.HALF_UP));
        mapping.setNumberOfRepayments(request.term().numberOfRepayments());
        mapping.setRepaymentEvery(request.term().repaymentEvery());
        mapping.setRepaymentFrequency(normalize(request.term().repaymentFrequency()));
        mapping.setInterestRatePerPeriod(request.interest().ratePerPeriod().setScale(4, RoundingMode.HALF_UP));
        mapping.setInterestType(normalize(request.interest().interestType()));
        mapping.setInterestCalculationPeriodType(normalize(request.interest().calculationPeriodType()));
        mapping.setInterestRateFrequency(normalize(request.interest().rateFrequency()));
        mapping.setAmortizationType(normalize(request.amortizationType()));
        mapping.setTransactionProcessingStrategyCode(trimToNull(request.transactionProcessingStrategyCode()) == null
                ? "ADVANCED-PAYMENT-ALLOCATION-STRATEGY"
                : request.transactionProcessingStrategyCode().trim());
        mapping.setAccountingTemplateCode(normalize(request.accountingTemplateCode()));
        mapping.setFineractProductId(Long.parseLong(fineractProductId));
        mapping.setActive(Boolean.TRUE.equals(request.active()));
        mapping.setCreatedBy(actor.username());
        mapping.setUpdatedBy(actor.username());
        mapping = loanProductMappingRepository.save(mapping);
        return LoanProductCatalogDtos.LoanProductCatalogResponse.from(mapping);
    }

    @Transactional(readOnly = true)
    public LoanProductCatalogDtos.LoanProductCatalogResponse getCurrentTenantProduct(String productCode) {
        String tenantId = currentActorService.requireCurrentUser().tenantId();
        LoanProductMapping mapping = loanProductMappingRepository.findByTenantIdAndProductCodeIgnoreCase(tenantId, productCode)
                .orElseThrow(() -> new NotFoundException("Loan product not found"));
        return LoanProductCatalogDtos.LoanProductCatalogResponse.from(mapping);
    }

    @Transactional
    public LoanProductCatalogDtos.LoanProductCatalogResponse updateCurrentTenantProductByShortName(
            String shortName,
            LoanProductCatalogDtos.UpdateLoanProductRequest request) {
        var actor = currentActorService.requireCurrentUser();
        String tenantId = actor.tenantId();
        LoanProductMapping mapping = loanProductMappingRepository.findByTenantIdAndShortNameIgnoreCase(tenantId, shortName)
                .orElseThrow(() -> new NotFoundException("Loan product not found"));

        ResolvedLoanProductUpdate resolved = resolveUpdate(mapping, request);
        validateResolvedUpdate(resolved, request.accountingAccounts());

        ProductAccounts productAccounts = hasAllAccountingAccountOverrides(request.accountingAccounts())
                ? resolveAccounts(request.accountingAccounts())
                : null;

        Tenant tenant = tenantService.getRequiredTenant(tenantId);
        fineractGateway.updateLoanProduct(tenant, String.valueOf(mapping.getFineractProductId()), new UpdateLoanProductRequest(
                resolved.displayName(),
                resolved.shortName(),
                resolved.description(),
                resolved.currencyCode(),
                resolved.defaultPrincipal(),
                resolved.minPrincipal(),
                resolved.maxPrincipal(),
                resolved.numberOfRepayments(),
                resolved.repaymentEvery(),
                repaymentFrequencyType(resolved.repaymentFrequency()),
                resolved.interestRatePerPeriod(),
                interestRateFrequencyType(resolved.interestRateFrequency()),
                interestType(resolved.interestType()),
                interestCalculationPeriodType(resolved.interestCalculationPeriodType()),
                amortizationType(resolved.amortizationType()),
                resolved.transactionProcessingStrategyCode(),
                productAccounts == null ? null : productAccounts.loanPortfolioAccountId(),
                productAccounts == null ? null : productAccounts.fundSourceAccountId(),
                productAccounts == null ? null : productAccounts.interestOnLoanAccountId(),
                productAccounts == null ? null : productAccounts.incomeFromFeeAccountId(),
                productAccounts == null ? null : productAccounts.incomeFromPenaltyAccountId(),
                productAccounts == null ? null : productAccounts.incomeFromRecoveryAccountId(),
                productAccounts == null ? null : productAccounts.writeOffAccountId(),
                productAccounts == null ? null : productAccounts.transfersInSuspenseAccountId(),
                productAccounts == null ? null : productAccounts.overpaymentLiabilityAccountId()));

        mapping.setDisplayName(resolved.displayName());
        mapping.setShortName(resolved.shortName());
        mapping.setDescription(resolved.description());
        mapping.setCurrencyCode(resolved.currencyCode());
        mapping.setPrincipalMin(resolved.minPrincipal());
        mapping.setPrincipalDefaultAmount(resolved.defaultPrincipal());
        mapping.setPrincipalMax(resolved.maxPrincipal());
        mapping.setNumberOfRepayments(resolved.numberOfRepayments());
        mapping.setRepaymentEvery(resolved.repaymentEvery());
        mapping.setRepaymentFrequency(normalize(resolved.repaymentFrequency()));
        mapping.setInterestRatePerPeriod(resolved.interestRatePerPeriod());
        mapping.setInterestType(normalize(resolved.interestType()));
        mapping.setInterestCalculationPeriodType(normalize(resolved.interestCalculationPeriodType()));
        mapping.setInterestRateFrequency(normalize(resolved.interestRateFrequency()));
        mapping.setAmortizationType(normalize(resolved.amortizationType()));
        mapping.setTransactionProcessingStrategyCode(resolved.transactionProcessingStrategyCode());
        mapping.setAccountingTemplateCode(normalize(resolved.accountingTemplateCode()));
        mapping.setActive(resolved.active());
        mapping.setUpdatedBy(actor.username());
        mapping = loanProductMappingRepository.save(mapping);
        return LoanProductCatalogDtos.LoanProductCatalogResponse.from(mapping);
    }

    @Transactional(readOnly = true)
    public PagedResponse<FineractDtos.LoanProductResponse> listCurrentTenantLoanProducts(
            Integer page,
            Integer size,
            String sortBy,
            String sortDir) {
        String tenantId = currentActorService.requireCurrentUser().tenantId();
        var pageable = PaginationSupport.pageable(page, size, sortBy, sortDir, LOAN_PRODUCT_SORTS, "name");
        String normalizedSortBy = PaginationSupport.normalizeSortBy(sortBy, LOAN_PRODUCT_SORTS, "name");
        String normalizedSortDir = PaginationSupport.normalizeDirectionValue(sortDir);
        var resultPage = loanProductMappingRepository.findAllByTenantIdAndActiveTrue(tenantId, pageable)
                .map(FineractDtos.LoanProductResponse::from);
        return PagedResponse.fromPage(resultPage, normalizedSortBy, normalizedSortDir);
    }

    private ResolvedLoanProductUpdate resolveUpdate(
            LoanProductMapping mapping,
            LoanProductCatalogDtos.UpdateLoanProductRequest request) {
        return new ResolvedLoanProductUpdate(
                request.displayName() == null ? mapping.getDisplayName() : request.displayName().trim(),
                request.shortName() == null ? mapping.getShortName() : request.shortName().trim().toUpperCase(),
                request.description() == null ? mapping.getDescription() : trimToNull(request.description()),
                request.currencyCode() == null ? mapping.getCurrencyCode() : request.currencyCode().trim().toUpperCase(),
                request.principal() != null && request.principal().min() != null
                        ? request.principal().min().setScale(2, RoundingMode.HALF_UP)
                        : mapping.getPrincipalMin(),
                request.principal() != null && request.principal().defaultAmount() != null
                        ? request.principal().defaultAmount().setScale(2, RoundingMode.HALF_UP)
                        : mapping.getPrincipalDefaultAmount(),
                request.principal() != null && request.principal().max() != null
                        ? request.principal().max().setScale(2, RoundingMode.HALF_UP)
                        : mapping.getPrincipalMax(),
                request.term() != null && request.term().numberOfRepayments() != null
                        ? request.term().numberOfRepayments()
                        : mapping.getNumberOfRepayments(),
                request.term() != null && request.term().repaymentEvery() != null
                        ? request.term().repaymentEvery()
                        : mapping.getRepaymentEvery(),
                request.term() != null && request.term().repaymentFrequency() != null
                        ? request.term().repaymentFrequency().trim()
                        : mapping.getRepaymentFrequency(),
                request.interest() != null && request.interest().ratePerPeriod() != null
                        ? request.interest().ratePerPeriod().setScale(4, RoundingMode.HALF_UP)
                        : mapping.getInterestRatePerPeriod(),
                request.interest() != null && request.interest().interestType() != null
                        ? request.interest().interestType().trim()
                        : mapping.getInterestType(),
                request.interest() != null && request.interest().calculationPeriodType() != null
                        ? request.interest().calculationPeriodType().trim()
                        : mapping.getInterestCalculationPeriodType(),
                request.interest() != null && request.interest().rateFrequency() != null
                        ? request.interest().rateFrequency().trim()
                        : mapping.getInterestRateFrequency(),
                request.amortizationType() == null ? mapping.getAmortizationType() : request.amortizationType().trim(),
                request.transactionProcessingStrategyCode() == null
                        ? mapping.getTransactionProcessingStrategyCode()
                        : defaultTransactionProcessingStrategyCode(request.transactionProcessingStrategyCode()),
                request.accountingTemplateCode() == null ? mapping.getAccountingTemplateCode() : request.accountingTemplateCode().trim(),
                request.active() == null ? mapping.isActive() : request.active());
    }

    private void validateRequest(LoanProductCatalogDtos.CreateLoanProductRequest request) {
        validateResolvedUpdate(new ResolvedLoanProductUpdate(
                request.displayName().trim(),
                request.shortName().trim().toUpperCase(),
                trimToNull(request.description()),
                request.currencyCode().trim().toUpperCase(),
                request.principal().min().setScale(2, RoundingMode.HALF_UP),
                request.principal().defaultAmount().setScale(2, RoundingMode.HALF_UP),
                request.principal().max().setScale(2, RoundingMode.HALF_UP),
                request.term().numberOfRepayments(),
                request.term().repaymentEvery(),
                request.term().repaymentFrequency().trim(),
                request.interest().ratePerPeriod().setScale(4, RoundingMode.HALF_UP),
                request.interest().interestType().trim(),
                request.interest().calculationPeriodType().trim(),
                request.interest().rateFrequency().trim(),
                request.amortizationType().trim(),
                defaultTransactionProcessingStrategyCode(request.transactionProcessingStrategyCode()),
                request.accountingTemplateCode().trim(),
                Boolean.TRUE.equals(request.active())), request.accountingAccounts());
    }

    private void validateResolvedUpdate(
            ResolvedLoanProductUpdate request,
            LoanProductCatalogDtos.AccountingAccountsRequest accountingAccounts) {
        if (request.numberOfRepayments() <= 0 || request.repaymentEvery() <= 0) {
            throw new BadRequestException("Repayment values must be greater than zero");
        }
        BigDecimal min = request.minPrincipal();
        BigDecimal def = request.defaultPrincipal();
        BigDecimal max = request.maxPrincipal();
        if (min.compareTo(def) > 0 || def.compareTo(max) > 0) {
            throw new BadRequestException("Principal values must satisfy min <= defaultAmount <= max");
        }
        if (request.shortName().length() > 4) {
            throw new BadRequestException("shortName must be at most 4 characters to satisfy Fineract constraints");
        }
        if (request.displayName().isBlank()) {
            throw new BadRequestException("displayName is required");
        }
        if (request.currencyCode().isBlank()) {
            throw new BadRequestException("currencyCode is required");
        }
        if (request.amortizationType().isBlank()) {
            throw new BadRequestException("amortizationType is required");
        }
        if (request.accountingTemplateCode().isBlank()) {
            throw new BadRequestException("accountingTemplateCode is required");
        }
        if (hasAnyAccountingAccountOverride(accountingAccounts) && !hasAllAccountingAccountOverrides(accountingAccounts)) {
            throw new BadRequestException("When providing direct accounting account IDs, all nine account IDs must be supplied");
        }
    }

    private ProductAccounts resolveAccounts(LoanProductCatalogDtos.AccountingAccountsRequest request) {
        if (hasAllAccountingAccountOverrides(request)) {
            return new ProductAccounts(
                    request.loanPortfolioAccountId(),
                    request.fundSourceAccountId(),
                    request.interestOnLoanAccountId(),
                    request.incomeFromFeeAccountId(),
                    request.incomeFromPenaltyAccountId(),
                    request.incomeFromRecoveryAccountId(),
                    request.writeOffAccountId(),
                    request.transfersInSuspenseAccountId(),
                    request.overpaymentLiabilityAccountId());
        }
        return new ProductAccounts(
                requiredGlAccount("LOAN_PORTFOLIO"),
                requiredGlAccount("BANK_SETTLEMENT"),
                requiredGlAccount("INTEREST_INCOME"),
                requiredGlAccount("FEE_INCOME"),
                requiredGlAccount("PENALTY_INCOME"),
                requiredGlAccount("INCOME_FROM_RECOVERY", "RECOVERY_INCOME"),
                requiredGlAccount("WRITE_OFF_EXPENSE_MAIN", "WRITE_OFF_EXPENSE"),
                requiredGlAccount("SUSPENSE_ACCOUNT"),
                requiredGlAccount("OVERPAYMENT_HOLDING"));
    }

    private boolean hasAnyAccountingAccountOverride(LoanProductCatalogDtos.AccountingAccountsRequest request) {
        return request != null && (
                request.loanPortfolioAccountId() != null
                        || request.fundSourceAccountId() != null
                        || request.interestOnLoanAccountId() != null
                        || request.incomeFromFeeAccountId() != null
                        || request.incomeFromPenaltyAccountId() != null
                        || request.incomeFromRecoveryAccountId() != null
                        || request.writeOffAccountId() != null
                        || request.transfersInSuspenseAccountId() != null
                        || request.overpaymentLiabilityAccountId() != null);
    }

    private boolean hasAllAccountingAccountOverrides(LoanProductCatalogDtos.AccountingAccountsRequest request) {
        return request != null
                && request.loanPortfolioAccountId() != null
                && request.fundSourceAccountId() != null
                && request.interestOnLoanAccountId() != null
                && request.incomeFromFeeAccountId() != null
                && request.incomeFromPenaltyAccountId() != null
                && request.incomeFromRecoveryAccountId() != null
                && request.writeOffAccountId() != null
                && request.transfersInSuspenseAccountId() != null
                && request.overpaymentLiabilityAccountId() != null;
    }

    private Long requiredGlAccount(String... businessPurposes) {
        String tenantId = currentActorService.requireCurrentUser().tenantId();
        List<GlAccountTemplate> templates = glAccountTemplateRepository.findAllByTenantIdOrderByTemplateCodeAsc(tenantId);
        for (String businessPurpose : businessPurposes) {
            var match = templates.stream()
                    .filter(item -> businessPurpose.equalsIgnoreCase(item.getBusinessPurpose()))
                    .findFirst();
            if (match.isPresent()) {
                return match.get().getFineractGlAccountId();
            }
        }
        throw new BadRequestException("Required GL account template is missing for business purpose " + String.join(" or ", businessPurposes));
    }

    private int repaymentFrequencyType(String repaymentFrequency) {
        return switch (normalize(repaymentFrequency)) {
            case "DAYS" -> 0;
            case "WEEKS" -> 1;
            case "MONTHS" -> 2;
            case "YEARS" -> 3;
            default -> throw new BadRequestException("Unsupported repayment frequency");
        };
    }

    private int interestRateFrequencyType(String rateFrequency) {
        return switch (normalize(rateFrequency)) {
            case "DAYS" -> 0;
            case "WEEKS" -> 3;
            case "MONTHS" -> 2;
            case "YEARS" -> 4;
            default -> throw new BadRequestException("Unsupported interest rate frequency");
        };
    }

    private int interestType(String interestType) {
        return switch (normalize(interestType)) {
            case "DECLINING_BALANCE" -> 0;
            case "FLAT" -> 1;
            default -> throw new BadRequestException("Unsupported interest type");
        };
    }

    private int interestCalculationPeriodType(String calculationPeriodType) {
        return switch (normalize(calculationPeriodType)) {
            case "DAILY" -> 0;
            case "SAME_AS_REPAYMENT_PERIOD" -> 1;
            default -> throw new BadRequestException("Unsupported interest calculation period type");
        };
    }

    private int amortizationType(String amortizationType) {
        return switch (normalize(amortizationType)) {
            case "EQUAL_INSTALLMENTS" -> 1;
            case "EQUAL_PRINCIPAL_PAYMENTS" -> 0;
            default -> throw new BadRequestException("Unsupported amortization type");
        };
    }

    private String normalize(String value) {
        return value.trim().toUpperCase();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String defaultTransactionProcessingStrategyCode(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? "ADVANCED-PAYMENT-ALLOCATION-STRATEGY" : trimmed;
    }

    private record ProductAccounts(
            Long loanPortfolioAccountId,
            Long fundSourceAccountId,
            Long interestOnLoanAccountId,
            Long incomeFromFeeAccountId,
            Long incomeFromPenaltyAccountId,
            Long incomeFromRecoveryAccountId,
            Long writeOffAccountId,
            Long transfersInSuspenseAccountId,
            Long overpaymentLiabilityAccountId) {
    }

    private record ResolvedLoanProductUpdate(
            String displayName,
            String shortName,
            String description,
            String currencyCode,
            BigDecimal minPrincipal,
            BigDecimal defaultPrincipal,
            BigDecimal maxPrincipal,
            Integer numberOfRepayments,
            Integer repaymentEvery,
            String repaymentFrequency,
            BigDecimal interestRatePerPeriod,
            String interestType,
            String interestCalculationPeriodType,
            String interestRateFrequency,
            String amortizationType,
            String transactionProcessingStrategyCode,
            String accountingTemplateCode,
            boolean active) {
    }
}
