package com.credvenn.lm.statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.credvenn.lm.common.exception.BadRequestException;
import com.credvenn.lm.tenant.Tenant;
import com.credvenn.lm.tenant.TenantService;
import com.credvenn.lm.tenant.TenantStatementProvider;
import java.util.List;
import org.junit.jupiter.api.Test;

class StatementProviderRegistryTest {
    @Test
    void resolvesTenantSelectionAndRejectsUnavailableProvider() {
        var tenantService = mock(TenantService.class);
        var properties = mock(StatementProviderProperties.class);
        var cladfy = mock(StatementAnalysisProvider.class);
        var tenant = new Tenant();
        tenant.setStatementProvider(TenantStatementProvider.CLADFY);
        when(tenantService.getRequiredTenant("tenant-1")).thenReturn(tenant);
        when(cladfy.providerCode()).thenReturn("CLADFY");
        var registry = new StatementProviderRegistry(List.of(cladfy), properties, tenantService);

        assertEquals("CLADFY", registry.providerForTenant("tenant-1"));
        assertSame(cladfy, registry.byCode(registry.providerForTenant("tenant-1")));
        tenant.setStatementProvider(TenantStatementProvider.INTERNAL_MPESA);
        assertEquals("INTERNAL_MPESA", registry.providerForTenant("tenant-1"));
        assertThrows(BadRequestException.class, () -> registry.byCode(registry.providerForTenant("tenant-1")));
    }
}
