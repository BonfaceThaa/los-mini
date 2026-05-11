package com.credvenn.lm.inventory;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public final class InventoryDtos {

    private InventoryDtos() {
    }

    @Schema(name = "CreateInventoryDeviceRequest")
    public record CreateInventoryDeviceRequest(
            @NotBlank @Size(max = 100) String serialNumber,
            @NotBlank @Size(max = 255) String deviceName,
            @NotBlank @Size(max = 100) String imei1,
            @Size(max = 100) String imei2,
            @NotNull @Positive BigDecimal cashPrice,
            @NotNull DepositType depositType,
            @NotNull @Positive BigDecimal depositValue) {
    }

    @Schema(name = "AssignInventoryDeviceRequest")
    public record AssignInventoryDeviceRequest(@NotBlank String deviceId) {
    }

    @Schema(name = "InventoryDeviceResponse")
    public record InventoryDeviceResponse(
            String id,
            String serialNumber,
            String deviceName,
            String imei1,
            String imei2,
            BigDecimal cashPrice,
            DepositType depositType,
            BigDecimal depositValue,
            InventoryDeviceStatus status,
            Instant createdAt,
            Instant updatedAt) {
    }

    @Schema(name = "InventoryDeviceAssignmentResponse")
    public record InventoryDeviceAssignmentResponse(
            String id,
            String applicationId,
            String deviceId,
            String assignedBy,
            Instant assignedAt) {
    }
}
