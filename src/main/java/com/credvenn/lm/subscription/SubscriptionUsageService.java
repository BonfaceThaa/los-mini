package com.credvenn.lm.subscription;

import com.credvenn.lm.application.ApplicationStatus;
import com.credvenn.lm.application.ApplicationStatusHistoryRepository;
import com.credvenn.lm.application.LoanRequestApplicationRepository;
import com.credvenn.lm.user.AppUserRepository;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionUsageService {

    private final TenantSubscriptionService tenantSubscriptionService;
    private final SubscriptionPlanService subscriptionPlanService;
    private final AppUserRepository appUserRepository;
    private final LoanRequestApplicationRepository applicationRepository;
    private final ApplicationStatusHistoryRepository statusHistoryRepository;
    private final TenantSubscriptionLedgerRepository ledgerRepository;
    private final SubscriptionBillingService subscriptionBillingService;

    public SubscriptionUsageService(
            TenantSubscriptionService tenantSubscriptionService,
            SubscriptionPlanService subscriptionPlanService,
            AppUserRepository appUserRepository,
            LoanRequestApplicationRepository applicationRepository,
            ApplicationStatusHistoryRepository statusHistoryRepository,
            TenantSubscriptionLedgerRepository ledgerRepository,
            SubscriptionBillingService subscriptionBillingService) {
        this.tenantSubscriptionService = tenantSubscriptionService;
        this.subscriptionPlanService = subscriptionPlanService;
        this.appUserRepository = appUserRepository;
        this.applicationRepository = applicationRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.ledgerRepository = ledgerRepository;
        this.subscriptionBillingService = subscriptionBillingService;
    }

    @Transactional(readOnly = true)
    public SubscriptionDtos.SubscriptionUsageResponse getCurrentUsage(String tenantId) {
        TenantSubscription subscription = tenantSubscriptionService.getRequiredEntity(
                tenantId,
                tenantSubscriptionService.getCurrent(tenantId).id());
        SubscriptionPlan plan = subscriptionPlanService.getRequiredEntity(subscription.getSubscriptionPlanId());
        long activeUsers = appUserRepository.countByTenantIdAndActiveTrue(tenantId);
        long createdApplications = applicationRepository.countByTenantIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                tenantId,
                subscription.getCurrentPeriodStart(),
                subscription.getCurrentPeriodEnd());
        long approvedApplications = statusHistoryRepository.countDistinctApplicationIdByTenantIdAndToStatusAndCreatedAtBetween(
                tenantId,
                ApplicationStatus.FINERACT_LOAN_ACTIVATED.name(),
                subscription.getCurrentPeriodStart(),
                subscription.getCurrentPeriodEnd());
        long successfulKycs = 0L;
        long successfulStatements = 0L;
        BigDecimal totalKycCharges = BigDecimal.ZERO;
        BigDecimal totalStatementCharges = BigDecimal.ZERO;
        for (TenantSubscriptionLedger entry : ledgerRepository.findAllBySubscriptionIdOrderByIdDesc(subscription.getId())) {
            if (entry.getEntryType() != SubscriptionLedgerEntryType.DEBIT) {
                continue;
            }
            if (entry.getChargeType() == SubscriptionChargeType.KYC_SUCCESS) {
                successfulKycs++;
                totalKycCharges = totalKycCharges.add(entry.getAmount());
            } else if (entry.getChargeType() == SubscriptionChargeType.STATEMENT_SUCCESS) {
                successfulStatements++;
                totalStatementCharges = totalStatementCharges.add(entry.getAmount());
            }
        }
        return new SubscriptionDtos.SubscriptionUsageResponse(
                tenantId,
                subscription.getId(),
                plan.getId(),
                plan.getPlanCode(),
                plan.getCurrency(),
                activeUsers,
                plan.getMaxUsers(),
                0L,
                plan.getMaxBranches(),
                createdApplications,
                plan.getMonthlyApplicationLimit(),
                approvedApplications,
                plan.getApprovedApplicationThreshold(),
                successfulKycs,
                successfulStatements,
                totalKycCharges,
                totalStatementCharges,
                subscription.getPrepaidBalance(),
                subscription.getTotalCredited(),
                subscription.getTotalDebited(),
                subscription.getPricingMode(),
                subscription.getNextPricingMode(),
                subscription.getCurrentPeriodStart(),
                subscription.getCurrentPeriodEnd(),
                subscription.getNextPricingModeEffectiveAt());
    }

    @Transactional(readOnly = true)
    public java.util.List<SubscriptionDtos.TenantSubscriptionLedgerResponse> getCurrentLedger(String tenantId) {
        return subscriptionBillingService.listCurrentLedger(tenantId);
    }

    static SubscriptionDtos.TenantSubscriptionLedgerResponse toLedgerResponse(TenantSubscriptionLedger entry) {
        return new SubscriptionDtos.TenantSubscriptionLedgerResponse(
                entry.getId(),
                entry.getTenantId(),
                entry.getSubscriptionId(),
                entry.getEntryType(),
                entry.getChargeType(),
                entry.getAmount(),
                entry.getCurrency(),
                entry.getReferenceType(),
                entry.getReferenceId(),
                entry.getBalanceBefore(),
                entry.getBalanceAfter(),
                entry.getNotes(),
                entry.getCreatedBy(),
                entry.getCreatedAt());
    }
}
