package com.credvenn.lm.devicecontrol;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class DeviceControlDtos {

    private DeviceControlDtos() {
    }

    @Schema(name = "UpsertTenantDeviceControlConfigRequest")
    public record UpsertTenantDeviceControlConfigRequest(
            boolean enabled,
            @NotBlank String baseUrl,
            @NotBlank String clientCode,
            @NotBlank String username,
            @NotBlank String password,
            String channelCode,
            String paymentLinkTemplate,
            boolean lockEnabled,
            boolean unlockEnabled,
            boolean remindersEnabled,
            boolean nudgesEnabled,
            boolean offlinePinEnabled,
            @NotNull @Min(1) Integer overdueLockCadenceMinutes,
            @NotNull @Min(1) Integer predueCadenceMinutes) {
    }

    @Schema(name = "TenantDeviceControlConfigResponse")
    public record TenantDeviceControlConfigResponse(
            String id,
            String tenantId,
            DeviceControlProvider provider,
            boolean enabled,
            String baseUrl,
            String clientCode,
            String channelCode,
            String paymentLinkTemplate,
            boolean lockEnabled,
            boolean unlockEnabled,
            boolean remindersEnabled,
            boolean nudgesEnabled,
            boolean offlinePinEnabled,
            Integer overdueLockCadenceMinutes,
            Integer predueCadenceMinutes,
            Instant lastOverdueLockRunAt,
            Instant lastPredueRunAt,
            boolean hasCredentials,
            Instant createdAt,
            Instant updatedAt) {
    }

    @Schema(name = "UpsertNotificationRuleRequest")
    public record UpsertNotificationRuleRequest(
            @NotNull Integer daysBeforeDue,
            @NotBlank String notificationCode,
            String name,
            boolean active) {
    }

    @Schema(name = "NotificationRuleResponse")
    public record NotificationRuleResponse(
            String id,
            Integer daysBeforeDue,
            String notificationCode,
            String name,
            boolean active,
            Instant createdAt,
            Instant updatedAt) {
    }

    @Schema(name = "UpsertCustomNotificationRuleFieldRequest")
    public record UpsertCustomNotificationRuleFieldRequest(
            @NotBlank String columnName,
            @NotNull CustomNotificationSourceField sourceField) {
    }

    @Schema(name = "UpsertCustomNotificationRuleRequest")
    public record UpsertCustomNotificationRuleRequest(
            @NotNull Integer daysBeforeDue,
            @NotBlank String notificationCode,
            String name,
            boolean active,
            @NotEmpty List<@Valid UpsertCustomNotificationRuleFieldRequest> fieldMappings) {
    }

    @Schema(name = "CustomNotificationRuleFieldResponse")
    public record CustomNotificationRuleFieldResponse(
            String id,
            String columnName,
            CustomNotificationSourceField sourceField,
            Integer displayOrder) {
    }

    @Schema(name = "CustomNotificationRuleResponse")
    public record CustomNotificationRuleResponse(
            String id,
            Integer daysBeforeDue,
            String notificationCode,
            String name,
            boolean active,
            List<CustomNotificationRuleFieldResponse> fieldMappings,
            Instant createdAt,
            Instant updatedAt) {
    }

    @Schema(name = "UpsertNudgeRuleRequest")
    public record UpsertNudgeRuleRequest(
            @NotNull Integer daysBeforeDue,
            @NotBlank String nudgeCode,
            String name,
            boolean active) {
    }

    @Schema(name = "NudgeRuleResponse")
    public record NudgeRuleResponse(
            String id,
            Integer daysBeforeDue,
            String nudgeCode,
            String name,
            boolean active,
            Instant createdAt,
            Instant updatedAt) {
    }

    @Schema(name = "ProviderCatalogItemResponse")
    public record ProviderCatalogItemResponse(
            String code,
            String title) {
    }

    @Schema(name = "ProviderCatalogResponse")
    public record ProviderCatalogResponse(
            String message,
            List<ProviderCatalogItemResponse> items) {
    }

    @Schema(name = "LoanDeviceControlStateResponse")
    public record LoanDeviceControlStateResponse(
            String id,
            String applicationId,
            String fineractLoanId,
            String deviceId,
            String imei1,
            String imei2,
            LoanDeviceControlCurrentState currentState,
            LocalDate lastDueDate,
            LocalDate nextDueDate,
            boolean hasOverdueInstallment,
            Long daysOverdue,
            Instant lastCollectionsEvaluatedAt,
            Instant lastLockActionAt,
            Instant lastUnlockActionAt,
            Instant lastNotificationActionAt,
            Instant lastNudgeActionAt,
            String lastProviderReference,
            Instant createdAt,
            Instant updatedAt) {
    }

    @Schema(name = "DeviceControlActionLogResponse")
    public record DeviceControlActionLogResponse(
            String id,
            String applicationId,
            String fineractLoanId,
            String deviceId,
            String imei1,
            DeviceControlActionType actionType,
            DeviceControlTriggerType triggerType,
            String ruleId,
            Integer ruleDayOffset,
            LocalDate dueDate,
            String providerTransactionId,
            DeviceControlActionStatus status,
            String failureReason,
            String requestedBy,
            Instant createdAt,
            Instant updatedAt) {
    }

    @Schema(name = "OfflinePinRequest")
    public record OfflinePinRequest(@NotBlank String passKey) {
    }

    @Schema(name = "OfflinePinResponse")
    public record OfflinePinResponse(String message, String passcode) {
    }

    @Schema(name = "DirectUnlockRequest")
    public record DirectUnlockRequest(@NotBlank String imei1) {
    }

    @Schema(name = "DirectUnlockResponse")
    public record DirectUnlockResponse(
            String message,
            String imei1,
            String applicationId,
            String fineractLoanId,
            String providerReference,
            Instant ranAt) {
    }

    @Schema(name = "OverdueLockSweepResponse")
    public record OverdueLockSweepResponse(
            String message,
            String tenantId,
            int evaluatedLoans,
            int overdueLoans,
            int queuedLocks,
            int succeededLocks,
            int failedLocks,
            String providerTransactionId,
            Instant ranAt) {
    }

    @Schema(name = "UnlockSweepResponse")
    public record UnlockSweepResponse(
            String message,
            String tenantId,
            int evaluatedLoans,
            int eligibleUnlocks,
            int queuedUnlocks,
            int succeededUnlocks,
            int failedUnlocks,
            String providerTransactionId,
            Instant ranAt) {
    }
}
