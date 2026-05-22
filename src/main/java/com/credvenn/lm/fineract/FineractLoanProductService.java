package com.credvenn.lm.fineract;

import com.credvenn.lm.security.CurrentActorService;
import com.credvenn.lm.tenant.Tenant;
import com.credvenn.lm.tenant.TenantService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FineractLoanProductService {

    private final CurrentActorService currentActorService;
    private final TenantService tenantService;
    private final FineractGateway fineractGateway;

    public FineractLoanProductService(
            CurrentActorService currentActorService,
            TenantService tenantService,
            FineractGateway fineractGateway) {
        this.currentActorService = currentActorService;
        this.tenantService = tenantService;
        this.fineractGateway = fineractGateway;
    }

    @Transactional(readOnly = true)
    public List<FineractDtos.LoanProductResponse> listCurrentTenantLoanProducts() {
        Tenant tenant = currentTenant();
        return fineractGateway.fetchActiveLoanProducts(tenant).stream().map(FineractDtos.LoanProductResponse::from).toList();
    }

    private Tenant currentTenant() {
        String tenantId = currentActorService.requireCurrentUser().tenantId();
        return tenantService.getRequiredTenant(tenantId);
    }
}
