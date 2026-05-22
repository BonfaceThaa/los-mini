package com.credvenn.lm.tenant;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/internal/tenants/{tenantId}/branding")
@Tag(name = "Tenant Branding")
@SecurityRequirement(name = "bearerAuth")
public class TenantBrandingController {

    private final TenantBrandingService tenantBrandingService;

    public TenantBrandingController(TenantBrandingService tenantBrandingService) {
        this.tenantBrandingService = tenantBrandingService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('TENANT_VIEW_ALL')")
    @Operation(summary = "Get tenant payment-page branding")
    public ResponseEntity<TenantBrandingDtos.TenantBrandingResponse> getBranding(@PathVariable String tenantId) {
        return ResponseEntity.ok(tenantBrandingService.getBranding(tenantId));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('TENANT_MANAGE_ALL')")
    @Operation(summary = "Create or update tenant payment-page branding")
    public ResponseEntity<TenantBrandingDtos.TenantBrandingResponse> upsertBranding(
            @PathVariable String tenantId,
            @Valid @RequestBody TenantBrandingDtos.UpsertTenantBrandingRequest request) {
        return ResponseEntity.ok(tenantBrandingService.upsertBranding(tenantId, request));
    }
}
