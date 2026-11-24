package com.credvenn.lm.statement;

import com.credvenn.lm.common.exception.BadRequestException;
import com.credvenn.lm.tenant.TenantService;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class StatementProviderRegistry {

    private final List<StatementAnalysisProvider> providers;
    private final StatementProviderProperties properties;
    private final TenantService tenantService;

    public StatementProviderRegistry(List<StatementAnalysisProvider> providers, StatementProviderProperties properties, TenantService tenantService) {
        this.providers = providers;
        this.properties = properties;
        this.tenantService = tenantService;
    }

    public StatementAnalysisProvider currentProvider() {
        return byCode(properties.provider());
    }

    public String providerForTenant(String tenantId) {
        return tenantService.getRequiredTenant(tenantId).getStatementProvider().name();
    }

    public StatementAnalysisProvider byCode(String code) {
        return providers.stream()
                .filter(provider -> provider.providerCode().equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Statement analysis provider is not available: " + code));
    }
}
