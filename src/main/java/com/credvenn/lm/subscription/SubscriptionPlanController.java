package com.credvenn.lm.subscription;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/internal/subscription/plans")
@Tag(name = "Subscription Plans")
@SecurityRequirement(name = "bearerAuth")
public class SubscriptionPlanController {

    private final SubscriptionPlanService subscriptionPlanService;

    public SubscriptionPlanController(SubscriptionPlanService subscriptionPlanService) {
        this.subscriptionPlanService = subscriptionPlanService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SUBSCRIPTION_PLAN_MANAGE')")
    @Operation(summary = "Create a platform subscription plan")
    public ResponseEntity<SubscriptionDtos.SubscriptionPlanResponse> create(
            @Valid @RequestBody SubscriptionDtos.CreateSubscriptionPlanRequest request) {
        SubscriptionDtos.SubscriptionPlanResponse response = subscriptionPlanService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{planId}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PutMapping("/{planId}")
    @PreAuthorize("hasAuthority('SUBSCRIPTION_PLAN_MANAGE')")
    @Operation(summary = "Update a platform subscription plan")
    public ResponseEntity<SubscriptionDtos.SubscriptionPlanResponse> update(
            @PathVariable String planId,
            @Valid @RequestBody SubscriptionDtos.UpdateSubscriptionPlanRequest request) {
        return ResponseEntity.ok(subscriptionPlanService.update(planId, request));
    }

    @GetMapping("/{planId}")
    @PreAuthorize("hasAuthority('TENANT_SUBSCRIPTION_VIEW') or hasAuthority('SUBSCRIPTION_PLAN_MANAGE')")
    @Operation(summary = "Get a subscription plan by id")
    public ResponseEntity<SubscriptionDtos.SubscriptionPlanResponse> getById(@PathVariable String planId) {
        return ResponseEntity.ok(subscriptionPlanService.getById(planId));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('TENANT_SUBSCRIPTION_VIEW') or hasAuthority('SUBSCRIPTION_PLAN_MANAGE')")
    @Operation(summary = "List platform subscription plans")
    public ResponseEntity<List<SubscriptionDtos.SubscriptionPlanResponse>> list() {
        return ResponseEntity.ok(subscriptionPlanService.list());
    }
}
