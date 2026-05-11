package com.credvenn.lm.statementinbox;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Inbound Statements")
public class TenantStatementInboxController {

    private final TenantStatementInboxService inboxService;

    public TenantStatementInboxController(TenantStatementInboxService inboxService) {
        this.inboxService = inboxService;
    }

    @PostMapping("/api/v1/internal/tenants/{tenantId}/statement-inboxes")
    @PreAuthorize("hasAuthority('TENANT_MANAGE_ALL')")
    @Operation(summary = "Create a dedicated inbound statement email mapping for a tenant")
    public ResponseEntity<TenantStatementInboxDtos.TenantStatementInboxResponse> create(
            @PathVariable String tenantId,
            @Valid @RequestBody TenantStatementInboxDtos.CreateTenantStatementInboxRequest request) {
        return ResponseEntity.ok(inboxService.create(tenantId, request));
    }

    @GetMapping("/api/v1/internal/tenants/{tenantId}/statement-inboxes")
    @PreAuthorize("hasAuthority('TENANT_VIEW_ALL')")
    @Operation(summary = "List inbound statement email mappings for a tenant")
    public ResponseEntity<List<TenantStatementInboxDtos.TenantStatementInboxResponse>> list(@PathVariable String tenantId) {
        return ResponseEntity.ok(inboxService.listByTenant(tenantId));
    }
}
