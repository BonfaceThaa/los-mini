package com.credvenn.lm.subscription;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class SubscriptionDtos {

    private SubscriptionDtos() {
    }

    @Schema(name = "CreateSubscriptionPlanRequest")
    public record CreateSubscriptionPlanRequest(
            @NotBlank @Size(max = 100) String planCode,
            @NotBlank @Size(max = 255) String name,
            @Size(max = 255) String description,
            @NotNull @PositiveOrZero Integer maxUsers,
            @NotNull @PositiveOrZero Integer maxBranches,
            @NotNull @PositiveOrZero Integer monthlyApplicationLimit,
            @NotNull @PositiveOrZero Integer approvedApplicationThreshold,
            @NotNull @PositiveOrZero BigDecimal monthlyFee,
            @NotNull @PositiveOrZero BigDecimal interestSharePercentage,
            @NotBlank @Size(max = 10) String currency) {
    }

    @Schema(name = "UpdateSubscriptionPlanRequest")
    public record UpdateSubscriptionPlanRequest(
            @NotBlank @Size(max = 255) String name,
            @Size(max = 255) String description,
            @NotNull @PositiveOrZero Integer maxUsers,
            @NotNull @PositiveOrZero Integer maxBranches,
            @NotNull @PositiveOrZero Integer monthlyApplicationLimit,
            @NotNull @PositiveOrZero Integer approvedApplicationThreshold,
            @NotNull @PositiveOrZero BigDecimal monthlyFee,
            @NotNull @PositiveOrZero BigDecimal interestSharePercentage,
            @NotBlank @Size(max = 10) String currency,
            boolean active) {
    }

    @Schema(name = "AssignTenantSubscriptionRequest")
    public record AssignTenantSubscriptionRequest(
            @NotBlank String subscriptionPlanId,
            @NotNull Instant currentPeriodStart,
            @NotNull Instant currentPeriodEnd,
            @Size(max = 1000) String operationalNotes) {
    }

    @Schema(name = "UpdateTenantSubscriptionRequest")
    public record UpdateTenantSubscriptionRequest(
            @NotBlank String subscriptionPlanId,
            @NotNull Instant currentPeriodStart,
            @NotNull Instant currentPeriodEnd,
            @NotNull TenantSubscriptionStatus status,
            @Size(max = 1000) String operationalNotes) {
    }

    @Schema(name = "UpdateSubscriptionNotesRequest")
    public record UpdateSubscriptionNotesRequest(@Size(max = 1000) String operationalNotes) {
    }

    @Schema(name = "SubscriptionPlanResponse")
    public record SubscriptionPlanResponse(
            String id,
            String planCode,
            String name,
            String description,
            int maxUsers,
            int maxBranches,
            int monthlyApplicationLimit,
            int approvedApplicationThreshold,
            BigDecimal monthlyFee,
            BigDecimal interestSharePercentage,
            String currency,
            boolean active,
            String createdBy,
            String updatedBy,
            Instant createdAt,
            Instant updatedAt) {
    }

    @Schema(name = "TenantSubscriptionResponse")
    public record TenantSubscriptionResponse(
            String id,
            String tenantId,
            String subscriptionPlanId,
            String subscriptionPlanCode,
            String subscriptionPlanName,
            TenantSubscriptionStatus status,
            SubscriptionPricingMode pricingMode,
            Instant currentPeriodStart,
            Instant currentPeriodEnd,
            Instant switchedToInterestShareAt,
            String operationalNotes,
            String createdBy,
            String updatedBy,
            Instant createdAt,
            Instant updatedAt) {
    }

    @Schema(name = "SubscriptionUsageResponse")
    public record SubscriptionUsageResponse(
            String tenantId,
            String subscriptionId,
            String subscriptionPlanId,
            String subscriptionPlanCode,
            long activeUsers,
            long maxUsers,
            long activeBranches,
            long maxBranches,
            long createdApplicationsInPeriod,
            long monthlyApplicationLimit,
            Instant currentPeriodStart,
            Instant currentPeriodEnd) {
    }
}
