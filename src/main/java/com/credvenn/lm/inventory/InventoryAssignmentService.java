package com.credvenn.lm.inventory;

import com.credvenn.lm.application.ApplicationService;
import com.credvenn.lm.common.logging.LoggingContext;
import com.credvenn.lm.common.exception.BadRequestException;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryAssignmentService {

    private static final Logger log = LoggerFactory.getLogger(InventoryAssignmentService.class);

    private final InventoryDeviceAssignmentRepository assignmentRepository;
    private final InventoryService inventoryService;
    private final ApplicationService applicationService;

    public InventoryAssignmentService(
            InventoryDeviceAssignmentRepository assignmentRepository,
            InventoryService inventoryService,
            ApplicationService applicationService) {
        this.assignmentRepository = assignmentRepository;
        this.inventoryService = inventoryService;
        this.applicationService = applicationService;
    }

    @Transactional
    public InventoryDtos.InventoryDeviceAssignmentResponse assign(
            String tenantId,
            String applicationId,
            String actor,
            InventoryDtos.AssignInventoryDeviceRequest request) {
        try (LoggingContext.Scope ignored = LoggingContext.withTenantAndApplication(tenantId, applicationId)) {
            var application = applicationService.getRequired(tenantId, applicationId);
            InventoryDevice requestedDevice = inventoryService.getRequiredDevice(tenantId, request.deviceId());
            InventoryDeviceAssignment assignment = assignmentRepository.findByApplicationId(applicationId).orElse(null);
            if (assignment != null && (application.isInternalApproved() || application.getFineractLoanId() != null)) {
                throw new BadRequestException("Device cannot be changed after internal approval has been completed");
            }
            if (assignment == null) {
                if (requestedDevice.getStatus() != InventoryDeviceStatus.AVAILABLE) {
                    throw new BadRequestException("Inventory device is not available");
                }
                assignment = new InventoryDeviceAssignment();
                assignment.setTenantId(tenantId);
                assignment.setApplicationId(applicationId);
            } else if (!assignment.getDeviceId().equals(requestedDevice.getId())) {
                InventoryDevice existingDevice = inventoryService.getRequiredDevice(tenantId, assignment.getDeviceId());
                existingDevice.setStatus(InventoryDeviceStatus.AVAILABLE);
                existingDevice.setLockStatus(InventoryDeviceLockStatus.CLEAR);
                if (requestedDevice.getStatus() != InventoryDeviceStatus.AVAILABLE) {
                    throw new BadRequestException("Inventory device is not available");
                }
                log.info("Reassigning application from deviceId={} to deviceId={}", existingDevice.getId(), requestedDevice.getId());
            }
            assignment.setDeviceId(requestedDevice.getId());
            assignment.setAssignedBy(actor);
            assignment.setAssignedAt(Instant.now());
            requestedDevice.setStatus(InventoryDeviceStatus.ASSIGNED);
            requestedDevice.setLockStatus(InventoryDeviceLockStatus.CLEAR);
            log.info("Assigning inventory device deviceId={} to application", requestedDevice.getId());
            applicationService.handleDeviceAssigned(tenantId, applicationId, actor, requestedDevice);
            return toResponse(assignmentRepository.save(assignment), requestedDevice);
        }
    }

    @Transactional(readOnly = true)
    public InventoryDtos.InventoryDeviceAssignmentResponse getByApplication(String tenantId, String applicationId) {
        applicationService.getRequired(tenantId, applicationId);
        return assignmentRepository.findByApplicationId(applicationId)
                .map(assignment -> toResponse(assignment, inventoryService.getRequiredDevice(tenantId, assignment.getDeviceId())))
                .orElse(null);
    }

    static InventoryDtos.InventoryDeviceAssignmentResponse toResponse(InventoryDeviceAssignment assignment, InventoryDevice device) {
        return new InventoryDtos.InventoryDeviceAssignmentResponse(
                assignment.getId(),
                assignment.getApplicationId(),
                assignment.getDeviceId(),
                device.getDeviceName(),
                device.getImei1(),
                device.getImei2(),
                assignment.getAssignedBy(),
                assignment.getAssignedAt());
    }
}
