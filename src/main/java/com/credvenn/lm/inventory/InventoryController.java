package com.credvenn.lm.inventory;

import com.credvenn.lm.common.api.PagedResponse;
import com.credvenn.lm.security.CurrentActorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SecurityRequirement(name = "bearerAuth")
public class InventoryController {

    private final InventoryService inventoryService;
    private final InventoryAssignmentService inventoryAssignmentService;
    private final CurrentActorService currentActorService;

    public InventoryController(
            InventoryService inventoryService,
            InventoryAssignmentService inventoryAssignmentService,
            CurrentActorService currentActorService) {
        this.inventoryService = inventoryService;
        this.inventoryAssignmentService = inventoryAssignmentService;
        this.currentActorService = currentActorService;
    }

    @PostMapping("/api/v1/inventory/devices")
    @PreAuthorize("hasAuthority('INVENTORY_MANAGE')")
    @Tag(name = "Inventory")
    @Operation(summary = "Create an inventory device")
    public ResponseEntity<InventoryDtos.InventoryDeviceResponse> create(@Valid @RequestBody InventoryDtos.CreateInventoryDeviceRequest request) {
        var actor = currentActorService.requireCurrentUser();
        return ResponseEntity.ok(inventoryService.create(actor.tenantId(), request));
    }

    @GetMapping("/api/v1/inventory/devices")
    @PreAuthorize("hasAuthority('INVENTORY_VIEW')")
    @Tag(name = "Inventory")
    @Operation(summary = "List inventory devices")
    public ResponseEntity<PagedResponse<InventoryDtos.InventoryDeviceResponse>> list(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(defaultValue = "deviceName") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        var actor = currentActorService.requireCurrentUser();
        return ResponseEntity.ok(inventoryService.list(actor.tenantId(), page, size, sortBy, sortDir));
    }

    @PostMapping("/api/v1/applications/{applicationId}/device-assignment")
    @PreAuthorize("hasAuthority('DEVICE_ASSIGN')")
    @Tag(name = "Inventory")
    @Operation(summary = "Assign a device to an application")
    public ResponseEntity<InventoryDtos.InventoryDeviceAssignmentResponse> assign(
            @PathVariable String applicationId,
            @Valid @RequestBody InventoryDtos.AssignInventoryDeviceRequest request) {
        var actor = currentActorService.requireCurrentUser();
        return ResponseEntity.ok(inventoryAssignmentService.assign(actor.tenantId(), applicationId, actor.username(), request));
    }

    @GetMapping("/api/v1/applications/{applicationId}/device-assignment")
    @PreAuthorize("hasAuthority('INVENTORY_VIEW')")
    @Tag(name = "Inventory")
    @Operation(summary = "Get device assignment for an application")
    public ResponseEntity<InventoryDtos.InventoryDeviceAssignmentResponse> getAssignment(@PathVariable String applicationId) {
        var actor = currentActorService.requireCurrentUser();
        return ResponseEntity.ok(inventoryAssignmentService.getByApplication(actor.tenantId(), applicationId));
    }
}
