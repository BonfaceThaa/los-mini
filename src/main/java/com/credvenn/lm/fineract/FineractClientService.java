package com.credvenn.lm.fineract;

import com.credvenn.lm.security.CurrentActorService;
import com.credvenn.lm.tenant.Tenant;
import com.credvenn.lm.tenant.TenantService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FineractClientService {

    private final CurrentActorService currentActorService;
    private final TenantService tenantService;
    private final FineractGateway fineractGateway;

    public FineractClientService(
            CurrentActorService currentActorService,
            TenantService tenantService,
            FineractGateway fineractGateway) {
        this.currentActorService = currentActorService;
        this.tenantService = tenantService;
        this.fineractGateway = fineractGateway;
    }

    @Transactional(readOnly = true)
    public List<FineractDtos.FineractClientResponse> listCurrentTenantClients() {
        Tenant tenant = currentTenant();
        return fineractGateway.fetchClients(tenant).stream().map(FineractDtos.FineractClientResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public FineractDtos.FineractClientResponse getCurrentTenantClient(String fineractClientId) {
        Tenant tenant = currentTenant();
        return FineractDtos.FineractClientResponse.from(fineractGateway.fetchClient(tenant, fineractClientId));
    }

    private Tenant currentTenant() {
        String tenantId = currentActorService.requireCurrentUser().tenantId();
        return tenantService.getRequiredTenant(tenantId);
    }
}
