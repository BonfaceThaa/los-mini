package com.credvenn.lm.subscription;

import com.credvenn.lm.security.CurrentActorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/subscription/current")
@Tag(name = "Current Tenant Subscription")
@SecurityRequirement(name = "bearerAuth")
public class CurrentTenantSubscriptionController {

    private final TenantSubscriptionService tenantSubscriptionService;
    private final SubscriptionUsageService subscriptionUsageService;
    private final CurrentActorService currentActorService;

    public CurrentTenantSubscriptionController(
            TenantSubscriptionService tenantSubscriptionService,
            SubscriptionUsageService subscriptionUsageService,
            CurrentActorService currentActorService) {
        this.tenantSubscriptionService = tenantSubscriptionService;
        this.subscriptionUsageService = subscriptionUsageService;
        this.currentActorService = currentActorService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('TENANT_SUBSCRIPTION_VIEW') or hasAuthority('TENANT_SUBSCRIPTION_MANAGE')")
    @Operation(summary = "Get the current active subscription for the authenticated tenant")
    public ResponseEntity<SubscriptionDtos.TenantSubscriptionResponse> getCurrent() {
        var actor = currentActorService.requireCurrentUser();
        return ResponseEntity.ok(tenantSubscriptionService.getCurrent(actor.tenantId()));
    }

    @GetMapping("/usage")
    @PreAuthorize("hasAuthority('TENANT_SUBSCRIPTION_VIEW') or hasAuthority('TENANT_SUBSCRIPTION_MANAGE')")
    @Operation(summary = "Get current subscription usage for the authenticated tenant")
    public ResponseEntity<SubscriptionDtos.SubscriptionUsageResponse> getCurrentUsage() {
        var actor = currentActorService.requireCurrentUser();
        return ResponseEntity.ok(subscriptionUsageService.getCurrentUsage(actor.tenantId()));
    }

    @GetMapping("/ledger")
    @PreAuthorize("hasAuthority('TENANT_SUBSCRIPTION_VIEW') or hasAuthority('TENANT_SUBSCRIPTION_MANAGE')")
    @Operation(summary = "Get current subscription ledger entries for the authenticated tenant")
    public ResponseEntity<List<SubscriptionDtos.TenantSubscriptionLedgerResponse>> getCurrentLedger() {
        var actor = currentActorService.requireCurrentUser();
        return ResponseEntity.ok(subscriptionUsageService.getCurrentLedger(actor.tenantId()));
    }
}
