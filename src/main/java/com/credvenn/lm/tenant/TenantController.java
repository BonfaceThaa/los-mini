package com.credvenn.lm.tenant;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/internal/tenants")
@Tag(name = "Tenants")
@SecurityRequirement(name = "bearerAuth")
public class TenantController {

    private final TenantService tenantService;

    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('TENANT_CREATE')")
    @Operation(summary = "Onboard a new business tenant and seed its initial tenant admin")
    public ResponseEntity<TenantDtos.TenantResponse> createTenant(@Valid @RequestBody TenantDtos.CreateTenantRequest request) {
        return ResponseEntity.ok(tenantService.createTenant(request));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('TENANT_VIEW_ALL')")
    @Operation(summary = "List all onboarded tenants")
    public ResponseEntity<List<TenantDtos.TenantResponse>> listTenants() {
        return ResponseEntity.ok(tenantService.listTenants());
    }

    @GetMapping("/{tenantId}")
    @PreAuthorize("hasAuthority('TENANT_VIEW_ALL')")
    @Operation(summary = "Get a tenant by id")
    public ResponseEntity<TenantDtos.TenantResponse> getTenant(@PathVariable String tenantId) {
        return ResponseEntity.ok(tenantService.getTenant(tenantId));
    }

    @PatchMapping("/{tenantId}/status")
    @PreAuthorize("hasAuthority('TENANT_MANAGE_ALL')")
    @Operation(summary = "Activate or deactivate a tenant")
    public ResponseEntity<TenantDtos.TenantResponse> updateStatus(
            @PathVariable String tenantId,
            @Valid @RequestBody TenantDtos.UpdateTenantStatusRequest request) {
        return ResponseEntity.ok(tenantService.updateStatus(tenantId, request));
    }

    @PatchMapping("/{tenantId}/kyc-mode")
    @PreAuthorize("hasAuthority('TENANT_MANAGE_ALL')")
    @Operation(summary = "Update tenant KYC flow mode")
    public ResponseEntity<TenantDtos.TenantResponse> updateKycMode(
            @PathVariable String tenantId,
            @Valid @RequestBody TenantDtos.UpdateTenantKycModeRequest request) {
        return ResponseEntity.ok(tenantService.updateKycMode(tenantId, request));
    }

    @PatchMapping("/{tenantId}/statement-analysis-mode")
    @PreAuthorize("hasAuthority('TENANT_MANAGE_ALL')")
    @Operation(summary = "Update tenant statement-analysis flow mode")
    public ResponseEntity<TenantDtos.TenantResponse> updateStatementAnalysisMode(
            @PathVariable String tenantId,
            @Valid @RequestBody TenantDtos.UpdateTenantStatementAnalysisModeRequest request) {
        return ResponseEntity.ok(tenantService.updateStatementAnalysisMode(tenantId, request));
    }
}
