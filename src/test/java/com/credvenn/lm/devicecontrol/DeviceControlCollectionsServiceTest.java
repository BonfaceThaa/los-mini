package com.credvenn.lm.devicecontrol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.credvenn.lm.application.ApplicationStatus;
import com.credvenn.lm.application.LoanRequestApplication;
import com.credvenn.lm.application.LoanRequestApplicationRepository;
import com.credvenn.lm.fineract.FineractGateway;
import com.credvenn.lm.inventory.InventoryDeviceRepository;
import com.credvenn.lm.tenant.Tenant;
import com.credvenn.lm.tenant.TenantService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DeviceControlCollectionsServiceTest {

    @Test
    void replayAutoLockCallsDatacultrForActivatedLoan() {
        TestContext context = new TestContext();
        TenantDeviceControlConfig config = enabledConfig();
        LoanRequestApplication application = activatedApplication("app-1", "loan-1", "device-1", "imei-1");
        LoanDeviceControlState state = stateFor(application, LoanDeviceControlCurrentState.CLEAR);
        FineractGateway.LoanCollectionsSnapshot snapshot = new FineractGateway.LoanCollectionsSnapshot(
                "loan-1",
                true,
                false,
                null,
                0,
                LocalDate.now().plusDays(5),
                List.of());

        when(context.applicationRepository.findByIdAndTenantId("app-1", "tenant-1")).thenReturn(Optional.of(application));
        when(context.configService.requireConfig("tenant-1")).thenReturn(config);
        when(context.stateRepository.findByApplicationId("app-1")).thenReturn(Optional.of(state));
        when(context.tenantService.getRequiredTenant("tenant-1")).thenReturn(new Tenant());
        when(context.fineractGateway.fetchLoanCollectionsSnapshot(any(Tenant.class), anyString())).thenReturn(snapshot);
        when(context.actionLogRepository.existsByApplicationIdAndDueDateAndActionTypeAndStatusIn(
                anyString(),
                any(LocalDate.class),
                any(DeviceControlActionType.class),
                any())).thenReturn(false, true);
        when(context.actionLogRepository.save(any(DeviceControlActionLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(context.configService.getRuntimeConfig("tenant-1")).thenReturn(runtimeConfig());
        when(context.deviceControlGateway.activateAutoLock(any(), anyString(), anyList()))
                .thenReturn(new DeviceControlGateway.BulkActionResult("tx-1", "request", "response"));

        DeviceControlDtos.ReplayAutoLockResponse response = context.service.replayAutoLock("tenant-1", "app-1", "operator");

        verify(context.deviceControlGateway).activateAutoLock(any(), anyString(), anyList());
        assertEquals("Auto-lock replay completed", response.message());
        assertTrue(response.state().autoLockAlreadyQueuedOrActivated());
        assertEquals(ApplicationStatus.FINERACT_LOAN_ACTIVATED, response.state().applicationStatus());
    }

    @Test
    void repaymentPostedStillUnlocksAndRearmsAutolockWhenLoanIsCleared() {
        TestContext context = new TestContext();
        TenantDeviceControlConfig config = enabledConfig();
        config.setUnlockEnabled(true);
        LoanRequestApplication application = activatedApplication("app-2", "loan-2", "device-2", "imei-2");
        LoanDeviceControlState state = stateFor(application, LoanDeviceControlCurrentState.LOCKED);
        FineractGateway.LoanCollectionsSnapshot snapshot = new FineractGateway.LoanCollectionsSnapshot(
                "loan-2",
                true,
                false,
                null,
                0,
                LocalDate.now().plusDays(7),
                List.of());

        when(context.configRepository.findByTenantId("tenant-1")).thenReturn(Optional.of(config));
        when(context.applicationRepository.findByIdAndTenantId("app-2", "tenant-1")).thenReturn(Optional.of(application));
        when(context.stateRepository.findByApplicationId("app-2")).thenReturn(Optional.of(state));
        when(context.tenantService.getRequiredTenant("tenant-1")).thenReturn(new Tenant());
        when(context.fineractGateway.fetchLoanCollectionsSnapshot(any(Tenant.class), anyString())).thenReturn(snapshot);
        when(context.actionLogRepository.existsByApplicationIdAndDueDateAndActionTypeAndStatusIn(
                anyString(),
                any(LocalDate.class),
                any(DeviceControlActionType.class),
                any())).thenReturn(false);
        when(context.actionLogRepository.save(any(DeviceControlActionLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(context.configService.getRuntimeConfig("tenant-1")).thenReturn(runtimeConfig());
        when(context.deviceControlGateway.unlock(any(), anyString(), nullable(String.class)))
                .thenReturn(new DeviceControlGateway.ActionResult("unlock-1", "unlock-request", "unlock-response"));
        when(context.deviceControlGateway.activateAutoLock(any(), anyString(), anyList()))
                .thenReturn(new DeviceControlGateway.BulkActionResult("autolock-1", "autolock-request", "autolock-response"));

        context.service.handleRepaymentPosted("tenant-1", "app-2", "loan-2", "receipt-9");

        verify(context.deviceControlGateway).unlock(any(), anyString(), nullable(String.class));
        verify(context.deviceControlGateway).activateAutoLock(any(), anyString(), anyList());
        assertEquals(LoanDeviceControlCurrentState.CLEAR, state.getCurrentState());
    }

    private static LoanRequestApplication activatedApplication(String applicationId, String loanId, String deviceId, String imei1) {
        LoanRequestApplication application = new LoanRequestApplication();
        setId(application, applicationId);
        application.setTenantId("tenant-1");
        application.setStatus(ApplicationStatus.FINERACT_LOAN_ACTIVATED);
        application.setFineractLoanId(loanId);
        application.setAssignedDeviceId(deviceId);
        application.setAssignedDeviceName("Demo Device");
        application.setAssignedDeviceImei1(imei1);
        return application;
    }

    private static LoanDeviceControlState stateFor(LoanRequestApplication application, LoanDeviceControlCurrentState currentState) {
        LoanDeviceControlState state = new LoanDeviceControlState();
        state.setTenantId(application.getTenantId());
        state.setApplicationId(application.getId());
        state.setFineractLoanId(application.getFineractLoanId());
        state.setDeviceId(application.getAssignedDeviceId());
        state.setImei1(application.getAssignedDeviceImei1());
        state.setCurrentState(currentState);
        return state;
    }

    private static TenantDeviceControlConfig enabledConfig() {
        TenantDeviceControlConfig config = new TenantDeviceControlConfig();
        config.setTenantId("tenant-1");
        config.setEnabled(true);
        config.setLockEnabled(true);
        config.setUnlockEnabled(true);
        return config;
    }

    private static DeviceControlGateway.RuntimeConfig runtimeConfig() {
        return new DeviceControlGateway.RuntimeConfig(
                "tenant-1",
                "cfg-1",
                "https://example.com",
                "client",
                "user",
                "pass",
                "channel",
                null);
    }

    private static void setId(LoanRequestApplication application, String id) {
        try {
            var field = LoanRequestApplication.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(application, id);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static final class TestContext {
        private final TenantDeviceControlConfigRepository configRepository = mock(TenantDeviceControlConfigRepository.class);
        private final TenantDeviceControlConfigService configService = mock(TenantDeviceControlConfigService.class);
        private final TenantDeviceControlNotificationRuleRepository notificationRuleRepository = mock(TenantDeviceControlNotificationRuleRepository.class);
        private final TenantDeviceControlCustomNotificationRuleRepository customNotificationRuleRepository = mock(TenantDeviceControlCustomNotificationRuleRepository.class);
        private final TenantDeviceControlCustomNotificationRuleFieldRepository customNotificationRuleFieldRepository =
                mock(TenantDeviceControlCustomNotificationRuleFieldRepository.class);
        private final TenantDeviceControlNudgeRuleRepository nudgeRuleRepository = mock(TenantDeviceControlNudgeRuleRepository.class);
        private final LoanDeviceControlStateRepository stateRepository = mock(LoanDeviceControlStateRepository.class);
        private final DeviceControlActionLogRepository actionLogRepository = mock(DeviceControlActionLogRepository.class);
        private final LoanRequestApplicationRepository applicationRepository = mock(LoanRequestApplicationRepository.class);
        private final InventoryDeviceRepository inventoryDeviceRepository = mock(InventoryDeviceRepository.class);
        private final TenantService tenantService = mock(TenantService.class);
        private final FineractGateway fineractGateway = mock(FineractGateway.class);
        private final DeviceControlGateway deviceControlGateway = mock(DeviceControlGateway.class);
        private final DeviceControlCollectionsService service = new DeviceControlCollectionsService(
                configRepository,
                configService,
                notificationRuleRepository,
                customNotificationRuleRepository,
                customNotificationRuleFieldRepository,
                nudgeRuleRepository,
                stateRepository,
                actionLogRepository,
                applicationRepository,
                inventoryDeviceRepository,
                tenantService,
                fineractGateway,
                deviceControlGateway);
    }
}
