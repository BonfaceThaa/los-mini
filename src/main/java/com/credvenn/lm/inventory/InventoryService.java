package com.credvenn.lm.inventory;

import com.credvenn.lm.common.exception.BadRequestException;
import com.credvenn.lm.common.exception.NotFoundException;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {

    private final InventoryDeviceRepository deviceRepository;

    public InventoryService(InventoryDeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    @Transactional
    public InventoryDtos.InventoryDeviceResponse create(String tenantId, InventoryDtos.CreateInventoryDeviceRequest request) {
        validateDevicePricing(request.cashPrice(), request.depositType(), request.depositValue());
        InventoryDevice device = new InventoryDevice();
        device.setTenantId(tenantId);
        device.setSerialNumber(request.serialNumber().trim());
        device.setDeviceName(request.deviceName().trim());
        device.setImei1(request.imei1().trim());
        device.setImei2(request.imei2() == null || request.imei2().isBlank() ? null : request.imei2().trim());
        device.setCashPrice(request.cashPrice());
        device.setDepositType(request.depositType());
        device.setDepositValue(request.depositValue());
        device.setStatus(InventoryDeviceStatus.AVAILABLE);
        return toResponse(deviceRepository.save(device));
    }

    @Transactional(readOnly = true)
    public List<InventoryDtos.InventoryDeviceResponse> list(String tenantId) {
        return deviceRepository.findAllByTenantIdOrderByDeviceNameAsc(tenantId).stream().map(InventoryService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public InventoryDevice getAvailableDevice(String tenantId, String deviceId) {
        InventoryDevice device = deviceRepository.findByIdAndTenantId(deviceId, tenantId)
                .orElseThrow(() -> new NotFoundException("Inventory device not found"));
        if (device.getStatus() != InventoryDeviceStatus.AVAILABLE) {
            throw new BadRequestException("Inventory device is not available");
        }
        return device;
    }

    @Transactional(readOnly = true)
    public InventoryDevice getRequiredDevice(String tenantId, String deviceId) {
        return deviceRepository.findByIdAndTenantId(deviceId, tenantId)
                .orElseThrow(() -> new NotFoundException("Inventory device not found"));
    }

    static InventoryDtos.InventoryDeviceResponse toResponse(InventoryDevice device) {
        return new InventoryDtos.InventoryDeviceResponse(
                device.getId(),
                device.getSerialNumber(),
                device.getDeviceName(),
                device.getImei1(),
                device.getImei2(),
                device.getCashPrice(),
                device.getDepositType(),
                device.getDepositValue(),
                device.getStatus(),
                device.getCreatedAt(),
                device.getUpdatedAt());
    }

    private void validateDevicePricing(BigDecimal cashPrice, DepositType depositType, BigDecimal depositValue) {
        if (cashPrice == null || cashPrice.signum() <= 0) {
            throw new BadRequestException("Cash price must be greater than zero");
        }
        if (depositValue == null || depositValue.signum() <= 0) {
            throw new BadRequestException("Deposit value must be greater than zero");
        }
        if (depositType == DepositType.PERCENTAGE && depositValue.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new BadRequestException("Percentage deposit cannot exceed 100");
        }
        if (depositType == DepositType.FIXED_AMOUNT && depositValue.compareTo(cashPrice) >= 0) {
            throw new BadRequestException("Fixed deposit must be less than the device cash price");
        }
    }
}
