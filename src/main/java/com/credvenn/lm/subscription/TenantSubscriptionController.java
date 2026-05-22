package com.credvenn.lm.subscription;

import com.credvenn.lm.security.CurrentActorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/internal/tenants/{tenantId}/subscriptions")
@Tag(name = "Tenant Subscriptions")
@SecurityRequirement(name = "bearerAuth")
public class TenantSubscriptionController {

    private final TenantSubscriptionService tenantSubscriptionService;
    private final SubscriptionUsageService subscriptionUsageService;
    private final SubscriptionBillingService subscriptionBillingService;
    private final CurrentActorService currentActorService;

    public TenantSubscriptionController(
            TenantSubscriptionService tenantSubscriptionService,
            SubscriptionUsageService subscriptionUsageService,
            SubscriptionBillingService subscriptionBillingService,
            CurrentActorService currentActorService) {
        this.tenantSubscriptionService = tenantSubscriptionService;
        this.subscriptionUsageService = subscriptionUsageService;
        this.subscriptionBillingService = subscriptionBillingService;
        this.currentActorService = currentActorService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('TENANT_SUBSCRIPTION_MANAGE')")
    @Operation(summary = "Assign a subscription plan to a tenant and activate it")
    public ResponseEntity<SubscriptionDtos.TenantSubscriptionResponse> assign(
            @PathVariable String tenantId,
            @Valid @RequestBody SubscriptionDtos.AssignTenantSubscriptionRequest request) {
        SubscriptionDtos.TenantSubscriptionResponse response = tenantSubscriptionService.assign(tenantId, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{subscriptionId}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PutMapping("/{subscriptionId}")
    @PreAuthorize("hasAuthority('TENANT_SUBSCRIPTION_MANAGE')")
    @Operation(summary = "Update a tenant subscription record")
    public ResponseEntity<SubscriptionDtos.TenantSubscriptionResponse> update(
            @PathVariable String tenantId,
            @PathVariable String subscriptionId,
            @Valid @RequestBody SubscriptionDtos.UpdateTenantSubscriptionRequest request) {
        return ResponseEntity.ok(tenantSubscriptionService.update(tenantId, subscriptionId, request));
    }

    @PatchMapping("/{subscriptionId}/notes")
    @PreAuthorize("hasAuthority('TENANT_SUBSCRIPTION_VIEW') or hasAuthority('TENANT_SUBSCRIPTION_MANAGE')")
    @Operation(summary = "Update operational notes for a tenant subscription")
    public ResponseEntity<SubscriptionDtos.TenantSubscriptionResponse> updateNotes(
            @PathVariable String tenantId,
            @PathVariable String subscriptionId,
            @Valid @RequestBody SubscriptionDtos.UpdateSubscriptionNotesRequest request) {
        return ResponseEntity.ok(tenantSubscriptionService.updateNotes(tenantId, subscriptionId, request));
    }

    @GetMapping("/current")
    @PreAuthorize("hasAuthority('TENANT_SUBSCRIPTION_VIEW') or hasAuthority('TENANT_SUBSCRIPTION_MANAGE')")
    @Operation(summary = "Get the current active subscription for a tenant")
    public ResponseEntity<SubscriptionDtos.TenantSubscriptionResponse> getCurrent(@PathVariable String tenantId) {
        return ResponseEntity.ok(tenantSubscriptionService.getCurrent(tenantId));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('TENANT_SUBSCRIPTION_VIEW') or hasAuthority('TENANT_SUBSCRIPTION_MANAGE')")
    @Operation(summary = "List the subscription history for a tenant")
    public ResponseEntity<List<SubscriptionDtos.TenantSubscriptionResponse>> history(@PathVariable String tenantId) {
        return ResponseEntity.ok(tenantSubscriptionService.history(tenantId));
    }

    @GetMapping("/current/usage")
    @PreAuthorize("hasAuthority('TENANT_SUBSCRIPTION_VIEW') or hasAuthority('TENANT_SUBSCRIPTION_MANAGE')")
    @Operation(summary = "Get current subscription usage for a tenant")
    public ResponseEntity<SubscriptionDtos.SubscriptionUsageResponse> getCurrentUsage(@PathVariable String tenantId) {
        return ResponseEntity.ok(subscriptionUsageService.getCurrentUsage(tenantId));
    }

    @GetMapping("/current/ledger")
    @PreAuthorize("hasAuthority('TENANT_SUBSCRIPTION_VIEW') or hasAuthority('TENANT_SUBSCRIPTION_MANAGE')")
    @Operation(summary = "Get current subscription ledger entries for a tenant")
    public ResponseEntity<List<SubscriptionDtos.TenantSubscriptionLedgerResponse>> getCurrentLedger(@PathVariable String tenantId) {
        return ResponseEntity.ok(subscriptionBillingService.listCurrentLedger(tenantId));
    }

    @PostMapping("/{subscriptionId}/top-ups")
    @PreAuthorize("hasAuthority('TENANT_SUBSCRIPTION_MANAGE')")
    @Operation(summary = "Credit prepaid balance for a tenant subscription")
    public ResponseEntity<SubscriptionDtos.TenantSubscriptionLedgerResponse> topUp(
            @PathVariable String tenantId,
            @PathVariable String subscriptionId,
            @Valid @RequestBody SubscriptionDtos.CreateSubscriptionTopUpRequest request) {
        String actor = currentActorService.requireCurrentUser().username();
        SubscriptionDtos.TenantSubscriptionLedgerResponse response = subscriptionBillingService.topUpPrepaid(
                tenantId,
                subscriptionId,
                request.amount(),
                request.notes(),
                actor);
        return ResponseEntity.ok(response);
    }
}
