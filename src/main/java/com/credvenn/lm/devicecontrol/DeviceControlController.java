package com.credvenn.lm.devicecontrol;

import com.credvenn.lm.security.CurrentActorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/device-control")
@Tag(name = "Device Control")
@SecurityRequirement(name = "bearerAuth")
public class DeviceControlController {

    private final CurrentActorService currentActorService;
    private final TenantDeviceControlConfigService configService;
    private final DeviceControlCollectionsService collectionsService;

    public DeviceControlController(
            CurrentActorService currentActorService,
            TenantDeviceControlConfigService configService,
            DeviceControlCollectionsService collectionsService) {
        this.currentActorService = currentActorService;
        this.configService = configService;
        this.collectionsService = collectionsService;
    }

    @GetMapping("/config")
    @PreAuthorize("hasAuthority('DEVICE_CONTROL_CONFIG_VIEW')")
    @Operation(summary = "Get the tenant's Datacultr device-control configuration")
    public ResponseEntity<DeviceControlDtos.TenantDeviceControlConfigResponse> getConfig() {
        var actor = currentActorService.requireCurrentUser();
        return ResponseEntity.ok(configService.get(actor.tenantId()));
    }

    @PutMapping("/config")
    @PreAuthorize("hasAuthority('DEVICE_CONTROL_CONFIG_MANAGE')")
    @Operation(summary = "Create or update the tenant's Datacultr device-control configuration")
    public ResponseEntity<DeviceControlDtos.TenantDeviceControlConfigResponse> upsertConfig(
            @Valid @RequestBody DeviceControlDtos.UpsertTenantDeviceControlConfigRequest request) {
        var actor = currentActorService.requireCurrentUser();
        return ResponseEntity.ok(configService.upsert(actor.tenantId(), request));
    }

    @GetMapping("/config/notification-rules")
    @PreAuthorize("hasAuthority('DEVICE_CONTROL_CONFIG_VIEW')")
    @Operation(summary = "List reminder notification rules for the tenant")
    public ResponseEntity<List<DeviceControlDtos.NotificationRuleResponse>> listNotificationRules() {
        var actor = currentActorService.requireCurrentUser();
        return ResponseEntity.ok(configService.listNotificationRules(actor.tenantId()));
    }

    @PostMapping("/config/notification-rules")
    @PreAuthorize("hasAuthority('DEVICE_CONTROL_CONFIG_MANAGE')")
    @Operation(summary = "Create a reminder notification rule")
    public ResponseEntity<DeviceControlDtos.NotificationRuleResponse> createNotificationRule(
            @Valid @RequestBody DeviceControlDtos.UpsertNotificationRuleRequest request) {
        var actor = currentActorService.requireCurrentUser();
        return ResponseEntity.ok(configService.createNotificationRule(actor.tenantId(), request));
    }

    @PatchMapping("/config/notification-rules/{ruleId}")
    @PreAuthorize("hasAuthority('DEVICE_CONTROL_CONFIG_MANAGE')")
    @Operation(summary = "Update a reminder notification rule")
    public ResponseEntity<DeviceControlDtos.NotificationRuleResponse> updateNotificationRule(
            @PathVariable String ruleId,
            @Valid @RequestBody DeviceControlDtos.UpsertNotificationRuleRequest request) {
        var actor = currentActorService.requireCurrentUser();
        return ResponseEntity.ok(configService.updateNotificationRule(actor.tenantId(), ruleId, request));
    }

    @DeleteMapping("/config/notification-rules/{ruleId}")
    @PreAuthorize("hasAuthority('DEVICE_CONTROL_CONFIG_MANAGE')")
    @Operation(summary = "Delete a reminder notification rule")
    public ResponseEntity<Void> deleteNotificationRule(@PathVariable String ruleId) {
        var actor = currentActorService.requireCurrentUser();
        configService.deleteNotificationRule(actor.tenantId(), ruleId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/config/custom-notification-rules")
    @PreAuthorize("hasAuthority('DEVICE_CONTROL_CONFIG_VIEW')")
    @Operation(summary = "List custom reminder notification rules for the tenant")
    public ResponseEntity<List<DeviceControlDtos.CustomNotificationRuleResponse>> listCustomNotificationRules() {
        var actor = currentActorService.requireCurrentUser();
        return ResponseEntity.ok(configService.listCustomNotificationRules(actor.tenantId()));
    }

    @PostMapping("/config/custom-notification-rules")
    @PreAuthorize("hasAuthority('DEVICE_CONTROL_CONFIG_MANAGE')")
    @Operation(summary = "Create a custom reminder notification rule with CSV field mappings")
    public ResponseEntity<DeviceControlDtos.CustomNotificationRuleResponse> createCustomNotificationRule(
            @Valid @RequestBody DeviceControlDtos.UpsertCustomNotificationRuleRequest request) {
        var actor = currentActorService.requireCurrentUser();
        return ResponseEntity.ok(configService.createCustomNotificationRule(actor.tenantId(), request));
    }

    @PatchMapping("/config/custom-notification-rules/{ruleId}")
    @PreAuthorize("hasAuthority('DEVICE_CONTROL_CONFIG_MANAGE')")
    @Operation(summary = "Update a custom reminder notification rule")
    public ResponseEntity<DeviceControlDtos.CustomNotificationRuleResponse> updateCustomNotificationRule(
            @PathVariable String ruleId,
            @Valid @RequestBody DeviceControlDtos.UpsertCustomNotificationRuleRequest request) {
        var actor = currentActorService.requireCurrentUser();
        return ResponseEntity.ok(configService.updateCustomNotificationRule(actor.tenantId(), ruleId, request));
    }

    @DeleteMapping("/config/custom-notification-rules/{ruleId}")
    @PreAuthorize("hasAuthority('DEVICE_CONTROL_CONFIG_MANAGE')")
    @Operation(summary = "Delete a custom reminder notification rule")
    public ResponseEntity<Void> deleteCustomNotificationRule(@PathVariable String ruleId) {
        var actor = currentActorService.requireCurrentUser();
        configService.deleteCustomNotificationRule(actor.tenantId(), ruleId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/config/nudge-rules")
    @PreAuthorize("hasAuthority('DEVICE_CONTROL_CONFIG_VIEW')")
    @Operation(summary = "List nudge rules for the tenant")
    public ResponseEntity<List<DeviceControlDtos.NudgeRuleResponse>> listNudgeRules() {
        var actor = currentActorService.requireCurrentUser();
        return ResponseEntity.ok(configService.listNudgeRules(actor.tenantId()));
    }

    @PostMapping("/config/nudge-rules")
    @PreAuthorize("hasAuthority('DEVICE_CONTROL_CONFIG_MANAGE')")
    @Operation(summary = "Create a nudge rule")
    public ResponseEntity<DeviceControlDtos.NudgeRuleResponse> createNudgeRule(
            @Valid @RequestBody DeviceControlDtos.UpsertNudgeRuleRequest request) {
        var actor = currentActorService.requireCurrentUser();
        return ResponseEntity.ok(configService.createNudgeRule(actor.tenantId(), request));
    }

    @PatchMapping("/config/nudge-rules/{ruleId}")
    @PreAuthorize("hasAuthority('DEVICE_CONTROL_CONFIG_MANAGE')")
    @Operation(summary = "Update a nudge rule")
    public ResponseEntity<DeviceControlDtos.NudgeRuleResponse> updateNudgeRule(
            @PathVariable String ruleId,
            @Valid @RequestBody DeviceControlDtos.UpsertNudgeRuleRequest request) {
        var actor = currentActorService.requireCurrentUser();
        return ResponseEntity.ok(configService.updateNudgeRule(actor.tenantId(), ruleId, request));
    }

    @DeleteMapping("/config/nudge-rules/{ruleId}")
    @PreAuthorize("hasAuthority('DEVICE_CONTROL_CONFIG_MANAGE')")
    @Operation(summary = "Delete a nudge rule")
    public ResponseEntity<Void> deleteNudgeRule(@PathVariable String ruleId) {
        var actor = currentActorService.requireCurrentUser();
        configService.deleteNudgeRule(actor.tenantId(), ruleId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/config/sync-notifications")
    @PreAuthorize("hasAuthority('DEVICE_CONTROL_CONFIG_VIEW')")
    @Operation(summary = "Fetch available Datacultr notifications for the tenant config")
    public ResponseEntity<DeviceControlDtos.ProviderCatalogResponse> syncNotifications() {
        var actor = currentActorService.requireCurrentUser();
        return ResponseEntity.ok(configService.fetchNotifications(actor.tenantId()));
    }

    @PostMapping("/config/sync-nudges")
    @PreAuthorize("hasAuthority('DEVICE_CONTROL_CONFIG_VIEW')")
    @Operation(summary = "Fetch available Datacultr nudges for the tenant config")
    public ResponseEntity<DeviceControlDtos.ProviderCatalogResponse> syncNudges() {
        var actor = currentActorService.requireCurrentUser();
        return ResponseEntity.ok(configService.fetchNudges(actor.tenantId()));
    }

    @GetMapping("/states")
    @PreAuthorize("hasAuthority('DEVICE_CONTROL_ACTION_VIEW')")
    @Operation(summary = "List current device-control states for the tenant")
    public ResponseEntity<List<DeviceControlDtos.LoanDeviceControlStateResponse>> listStates() {
        var actor = currentActorService.requireCurrentUser();
        return ResponseEntity.ok(collectionsService.listStates(actor.tenantId()));
    }

    @GetMapping("/actions")
    @PreAuthorize("hasAuthority('DEVICE_CONTROL_ACTION_VIEW')")
    @Operation(summary = "List device-control actions for the tenant")
    public ResponseEntity<List<DeviceControlDtos.DeviceControlActionLogResponse>> listActions() {
        var actor = currentActorService.requireCurrentUser();
        return ResponseEntity.ok(collectionsService.listActions(actor.tenantId()));
    }

    @PostMapping("/actions/{actionId}/retry")
    @PreAuthorize("hasAuthority('DEVICE_CONTROL_ACTION_MANAGE')")
    @Operation(summary = "Retry a failed device-control action")
    public ResponseEntity<Void> retryAction(@PathVariable String actionId) {
        var actor = currentActorService.requireCurrentUser();
        collectionsService.retryAction(actor.tenantId(), actionId, actor.username());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/actions/run-daily-overdue-locks")
    @PreAuthorize("hasAuthority('DEVICE_CONTROL_ACTION_MANAGE')")
    @Operation(summary = "Run the daily overdue-loan lock sweep for the current tenant")
    public ResponseEntity<DeviceControlDtos.OverdueLockSweepResponse> runDailyOverdueLocks() {
        var actor = currentActorService.requireCurrentUser();
        return ResponseEntity.accepted().body(collectionsService.runDailyOverdueLockSweep(actor.tenantId(), actor.username()));
    }

    @PostMapping("/actions/run-daily-unlocks")
    @PreAuthorize("hasAuthority('DEVICE_CONTROL_ACTION_MANAGE')")
    @Operation(summary = "Run the daily cleared-loan unlock sweep for the current tenant")
    public ResponseEntity<DeviceControlDtos.UnlockSweepResponse> runDailyUnlocks() {
        var actor = currentActorService.requireCurrentUser();
        return ResponseEntity.accepted().body(collectionsService.runDailyUnlockSweep(actor.tenantId(), actor.username()));
    }

    @PostMapping("/actions/unlock")
    @PreAuthorize("hasAuthority('DEVICE_CONTROL_ACTION_MANAGE')")
    @Operation(summary = "Unlock a single tenant device by IMEI 1")
    public ResponseEntity<DeviceControlDtos.DirectUnlockResponse> unlockByImei(
            @Valid @RequestBody DeviceControlDtos.DirectUnlockRequest request) {
        var actor = currentActorService.requireCurrentUser();
        return ResponseEntity.accepted().body(collectionsService.unlockByImei(actor.tenantId(), request, actor.username()));
    }

    @PostMapping("/applications/{applicationId}/offline-pin")
    @PreAuthorize("hasAuthority('DEVICE_OFFLINE_PIN_VIEW')")
    @Operation(summary = "Get an offline unlock PIN for a locked device")
    public ResponseEntity<DeviceControlDtos.OfflinePinResponse> getOfflinePin(
            @PathVariable String applicationId,
            @Valid @RequestBody DeviceControlDtos.OfflinePinRequest request) {
        var actor = currentActorService.requireCurrentUser();
        return ResponseEntity.ok(collectionsService.getOfflinePin(actor.tenantId(), applicationId, request, actor.username()));
    }
}
