package com.credvenn.lm.fineract;

import com.credvenn.lm.application.LoanRequestApplicationRepository;
import com.credvenn.lm.common.exception.NotFoundException;
import com.credvenn.lm.security.CurrentActorService;
import com.credvenn.lm.tenant.Tenant;
import com.credvenn.lm.tenant.TenantService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FineractLoanService {

    private final CurrentActorService currentActorService;
    private final TenantService tenantService;
    private final LoanRequestApplicationRepository applicationRepository;
    private final FineractGateway fineractGateway;

    public FineractLoanService(
            CurrentActorService currentActorService,
            TenantService tenantService,
            LoanRequestApplicationRepository applicationRepository,
            FineractGateway fineractGateway) {
        this.currentActorService = currentActorService;
        this.tenantService = tenantService;
        this.applicationRepository = applicationRepository;
        this.fineractGateway = fineractGateway;
    }

    @Transactional(readOnly = true)
    public FineractDtos.RepaymentScheduleResponse getCurrentTenantRepaymentSchedule(String loanId) {
        String tenantId = currentActorService.requireCurrentUser().tenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new NotFoundException("Loan not found");
        }
        applicationRepository.findByTenantIdAndFineractLoanId(tenantId, loanId)
                .orElseThrow(() -> new NotFoundException("Loan not found"));
        Tenant tenant = tenantService.getRequiredTenant(tenantId);
        return FineractDtos.RepaymentScheduleResponse.from(fineractGateway.fetchLoanRepaymentSchedule(tenant, loanId));
    }

    @Transactional(readOnly = true)
    public FineractDtos.JournalEntryListResponse getCurrentTenantJournalEntries(FineractGateway.JournalEntryQuery query) {
        String tenantId = currentActorService.requireCurrentUser().tenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new NotFoundException("Tenant not found");
        }
        Tenant tenant = tenantService.getRequiredTenant(tenantId);
        return FineractDtos.JournalEntryListResponse.from(fineractGateway.fetchJournalEntries(tenant, query));
    }
}
