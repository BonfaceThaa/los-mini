package com.credvenn.lm.devicecontrol;

import com.credvenn.lm.application.ApplicationStatus;
import com.credvenn.lm.application.LoanRequestApplication;
import com.credvenn.lm.application.LoanRequestApplicationRepository;
import com.credvenn.lm.common.exception.BadRequestException;
import com.credvenn.lm.common.exception.NotFoundException;
import com.credvenn.lm.fineract.FineractGateway;
import com.credvenn.lm.inventory.InventoryDevice;
import com.credvenn.lm.inventory.InventoryDeviceLockStatus;
import com.credvenn.lm.inventory.InventoryDeviceRepository;
import com.credvenn.lm.tenant.Tenant;
import com.credvenn.lm.tenant.TenantService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceControlCollectionsService {

    private static final Logger log = LoggerFactory.getLogger(DeviceControlCollectionsService.class);
    private static final int FINERACT_PAGE_SIZE = 200;
    private static final ZoneId NAIROBI_ZONE = ZoneId.of("Africa/Nairobi");
    private static final LocalTime AUTO_LOCK_TIME_NAIROBI = LocalTime.of(6, 0);

    private final TenantDeviceControlConfigRepository configRepository;
    private final TenantDeviceControlConfigService configService;
    private final TenantDeviceControlNotificationRuleRepository notificationRuleRepository;
    private final TenantDeviceControlCustomNotificationRuleRepository customNotificationRuleRepository;
    private final TenantDeviceControlCustomNotificationRuleFieldRepository customNotificationRuleFieldRepository;
    private final TenantDeviceControlNudgeRuleRepository nudgeRuleRepository;
    private final LoanDeviceControlStateRepository stateRepository;
    private final DeviceControlActionLogRepository actionLogRepository;
    private final LoanRequestApplicationRepository applicationRepository;
    private final InventoryDeviceRepository inventoryDeviceRepository;
    private final TenantService tenantService;
    private final FineractGateway fineractGateway;
    private final DeviceControlGateway deviceControlGateway;

    public DeviceControlCollectionsService(
            TenantDeviceControlConfigRepository configRepository,
            TenantDeviceControlConfigService configService,
            TenantDeviceControlNotificationRuleRepository notificationRuleRepository,
            TenantDeviceControlCustomNotificationRuleRepository customNotificationRuleRepository,
            TenantDeviceControlCustomNotificationRuleFieldRepository customNotificationRuleFieldRepository,
            TenantDeviceControlNudgeRuleRepository nudgeRuleRepository,
            LoanDeviceControlStateRepository stateRepository,
            DeviceControlActionLogRepository actionLogRepository,
            LoanRequestApplicationRepository applicationRepository,
            InventoryDeviceRepository inventoryDeviceRepository,
            TenantService tenantService,
            FineractGateway fineractGateway,
            DeviceControlGateway deviceControlGateway) {
        this.configRepository = configRepository;
        this.configService = configService;
        this.notificationRuleRepository = notificationRuleRepository;
        this.customNotificationRuleRepository = customNotificationRuleRepository;
        this.customNotificationRuleFieldRepository = customNotificationRuleFieldRepository;
        this.nudgeRuleRepository = nudgeRuleRepository;
        this.stateRepository = stateRepository;
        this.actionLogRepository = actionLogRepository;
        this.applicationRepository = applicationRepository;
        this.inventoryDeviceRepository = inventoryDeviceRepository;
        this.tenantService = tenantService;
        this.fineractGateway = fineractGateway;
        this.deviceControlGateway = deviceControlGateway;
    }

    @Transactional
    public void runScheduledSweeps() {
        Instant now = Instant.now();
        for (TenantDeviceControlConfig config : configRepository.findAllByEnabledTrueOrderByTenantIdAsc()) {
            try {
                if (shouldRun(config.getLastOverdueLockRunAt(), config.getOverdueLockCadenceMinutes(), now)) {
                    runOverdueAndPredueSweep(config.getTenantId(), true, false);
                    config.setLastOverdueLockRunAt(now);
                }
                if (shouldRun(config.getLastPredueRunAt(), config.getPredueCadenceMinutes(), now)) {
                    runOverdueAndPredueSweep(config.getTenantId(), false, true);
                    config.setLastPredueRunAt(now);
                }
            } catch (Exception ex) {
                log.error("Device-control scheduled sweep failed for tenantId={}", config.getTenantId(), ex);
            }
        }
    }

    @Transactional
    public void runDailyOverdueLockSweeps() {
        for (TenantDeviceControlConfig config : configRepository.findAllByEnabledTrueOrderByTenantIdAsc()) {
            try {
                runDailyOverdueLockSweep(config.getTenantId(), "system");
            } catch (Exception ex) {
                log.error("Device-control daily overdue lock sweep failed for tenantId={}", config.getTenantId(), ex);
            }
        }
    }

    @Transactional
    public void runDailyUnlockSweeps() {
        for (TenantDeviceControlConfig config : configRepository.findAllByEnabledTrueOrderByTenantIdAsc()) {
            try {
                runDailyUnlockSweep(config.getTenantId(), "system");
            } catch (Exception ex) {
                log.error("Device-control daily unlock sweep failed for tenantId={}", config.getTenantId(), ex);
            }
        }
    }

    @Transactional
    public DeviceControlDtos.OverdueLockSweepResponse runDailyOverdueLockSweep(String tenantId, String actor) {
        TenantDeviceControlConfig config = configService.requireConfig(tenantId);
        if (!config.isEnabled()) {
            throw new BadRequestException("Device control is not enabled for this tenant");
        }
        if (!config.isLockEnabled()) {
            throw new BadRequestException("Device lock actions are not enabled for this tenant");
        }

        Tenant tenant = tenantService.getRequiredTenant(tenantId);
        List<LoanRequestApplication> applications = applicationRepository
                .findAllByTenantIdAndStatusAndAssignedDeviceIdIsNotNullAndAssignedDeviceImei1IsNotNullAndFineractLoanIdIsNotNullOrderByCreatedAtAsc(
                        tenantId,
                        ApplicationStatus.FINERACT_LOAN_ACTIVATED);
        if (applications.isEmpty()) {
            config.setLastOverdueLockRunAt(Instant.now());
            return new DeviceControlDtos.OverdueLockSweepResponse(
                    "No active tenant loans were eligible for device-control locking",
                    tenantId,
                    0,
                    0,
                    0,
                    0,
                    0,
                    null,
                    Instant.now());
        }

        Map<String, LoanRequestApplication> byLoanId = new HashMap<>();
        for (LoanRequestApplication application : applications) {
            byLoanId.put(application.getFineractLoanId(), application);
        }

        int evaluatedLoans = 0;
        int overdueLoans = 0;
        List<PendingBulkLock> pendingLocks = new ArrayList<>();
        int offset = 0;
        while (true) {
            FineractGateway.LoanPage page = fineractGateway.fetchLoansPage(tenant, offset, FINERACT_PAGE_SIZE);
            for (FineractGateway.LoanPageItem item : page.items()) {
                if (!item.active()) {
                    continue;
                }
                LoanRequestApplication application = byLoanId.get(item.id());
                if (application == null) {
                    continue;
                }
                evaluatedLoans++;
                LoanDeviceControlState state = ensureState(application);
                FineractGateway.LoanCollectionsSnapshot snapshot = fineractGateway.fetchLoanCollectionsSnapshot(tenant, item.id());
                applySnapshot(state, snapshot);
                if (snapshot.hasOverdueInstallment()) {
                    overdueLoans++;
                    queueLock(config, application, state, DeviceControlTriggerType.SCHEDULED_OVERDUE, "daily-overdue-lock-sweep", actor, pendingLocks);
                }
            }
            if (!page.hasNext()) {
                break;
            }
            offset += page.limit();
        }

        BulkFlushSummary summary = flushLocks(config, pendingLocks);
        Instant ranAt = Instant.now();
        config.setLastOverdueLockRunAt(ranAt);
        return new DeviceControlDtos.OverdueLockSweepResponse(
                summary.queuedLocks() == 0
                        ? "No new overdue-device locks were queued"
                        : "Daily overdue-loan lock sweep completed",
                tenantId,
                evaluatedLoans,
                overdueLoans,
                summary.queuedLocks(),
                summary.succeededLocks(),
                summary.failedLocks(),
                summary.providerTransactionId(),
                ranAt);
    }

    @Transactional
    public DeviceControlDtos.UnlockSweepResponse runDailyUnlockSweep(String tenantId, String actor) {
        TenantDeviceControlConfig config = configService.requireConfig(tenantId);
        if (!config.isEnabled()) {
            throw new BadRequestException("Device control is not enabled for this tenant");
        }
        if (!config.isUnlockEnabled()) {
            throw new BadRequestException("Device unlock actions are not enabled for this tenant");
        }

        Tenant tenant = tenantService.getRequiredTenant(tenantId);
        List<LoanRequestApplication> applications = applicationRepository
                .findAllByTenantIdAndStatusAndAssignedDeviceIdIsNotNullAndAssignedDeviceImei1IsNotNullAndFineractLoanIdIsNotNullOrderByCreatedAtAsc(
                        tenantId,
                        ApplicationStatus.FINERACT_LOAN_ACTIVATED);
        if (applications.isEmpty()) {
            return new DeviceControlDtos.UnlockSweepResponse(
                    "No active tenant loans were eligible for device-control unlocking",
                    tenantId,
                    0,
                    0,
                    0,
                    0,
                    0,
                    null,
                    Instant.now());
        }

        Map<String, LoanRequestApplication> byLoanId = new HashMap<>();
        for (LoanRequestApplication application : applications) {
            byLoanId.put(application.getFineractLoanId(), application);
        }

        int evaluatedLoans = 0;
        int eligibleUnlocks = 0;
        List<PendingBulkUnlock> pendingUnlocks = new ArrayList<>();
        int offset = 0;
        while (true) {
            FineractGateway.LoanPage page = fineractGateway.fetchLoansPage(tenant, offset, FINERACT_PAGE_SIZE);
            for (FineractGateway.LoanPageItem item : page.items()) {
                if (!item.active()) {
                    continue;
                }
                LoanRequestApplication application = byLoanId.get(item.id());
                if (application == null) {
                    continue;
                }
                evaluatedLoans++;
                LoanDeviceControlState state = ensureState(application);
                FineractGateway.LoanCollectionsSnapshot snapshot = fineractGateway.fetchLoanCollectionsSnapshot(tenant, item.id());
                applySnapshot(state, snapshot);
                if (!snapshot.hasOverdueInstallment() && canQueueUnlock(state)) {
                    eligibleUnlocks++;
                    queueUnlock(config, application, state, DeviceControlTriggerType.SCHEDULED_CLEAR, "daily-unlock-sweep", actor, pendingUnlocks);
                }
            }
            if (!page.hasNext()) {
                break;
            }
            offset += page.limit();
        }

        BulkFlushSummary summary = flushUnlocks(config, pendingUnlocks);
        Instant ranAt = Instant.now();
        return new DeviceControlDtos.UnlockSweepResponse(
                summary.queuedLocks() == 0
                        ? "No blocked devices were eligible for bulk unlock"
                        : "Daily cleared-loan unlock sweep completed",
                tenantId,
                evaluatedLoans,
                eligibleUnlocks,
                summary.queuedLocks(),
                summary.succeededLocks(),
                summary.failedLocks(),
                summary.providerTransactionId(),
                ranAt);
    }

    @Async
    @Transactional
    public void handleLoanActivated(String tenantId, String applicationId, String fineractLoanId, String actor) {
        TenantDeviceControlConfig config = configRepository.findByTenantId(tenantId).orElse(null);
        if (config == null || !config.isEnabled() || !config.isLockEnabled()) {
            return;
        }
        LoanRequestApplication application = applicationRepository.findByIdAndTenantId(applicationId, tenantId)
                .orElseThrow(() -> new NotFoundException("Loan application not found"));
        LoanDeviceControlState state = ensureState(application);
        Tenant tenant = tenantService.getRequiredTenant(tenantId);
        FineractGateway.LoanCollectionsSnapshot snapshot = fineractGateway.fetchLoanCollectionsSnapshot(tenant, fineractLoanId);
        applySnapshot(state, snapshot);
        activateAutoLockIfEligible(config, application, state, snapshot.nextDueDate(), DeviceControlTriggerType.LOAN_ACTIVATED, "loan-activation", actor);
    }

    @Async
    @Transactional
    public void handleRepaymentPosted(String tenantId, String applicationId, String fineractLoanId, String receiptId) {
        TenantDeviceControlConfig config = configRepository.findByTenantId(tenantId).orElse(null);
        if (config == null || !config.isEnabled() || (!config.isUnlockEnabled() && !config.isLockEnabled())) {
            return;
        }
        LoanRequestApplication application = applicationRepository.findByIdAndTenantId(applicationId, tenantId)
                .orElseThrow(() -> new NotFoundException("Loan application not found"));
        LoanDeviceControlState state = ensureState(application);
        Tenant tenant = tenantService.getRequiredTenant(tenantId);
        FineractGateway.LoanCollectionsSnapshot snapshot = fineractGateway.fetchLoanCollectionsSnapshot(tenant, fineractLoanId);
        applySnapshot(state, snapshot);
        if (snapshot.hasOverdueInstallment()) {
            log.info("Keeping device locked because repayment did not clear overdue state applicationId={} receiptId={}", applicationId, receiptId);
            return;
        }
        if (state.getCurrentState() == LoanDeviceControlCurrentState.LOCKED && config.isUnlockEnabled()) {
            unlockDevice(config, application, state, DeviceControlTriggerType.PAYMENT_POSTED, "mpesa-receipt:" + receiptId, "system");
        }
        activateAutoLockIfEligible(config, application, state, snapshot.nextDueDate(), DeviceControlTriggerType.PAYMENT_POSTED, "mpesa-receipt:" + receiptId, "system");
    }

    @Transactional(readOnly = true)
    public List<DeviceControlDtos.LoanDeviceControlStateResponse> listStates(String tenantId) {
        return stateRepository.findAllByTenantIdOrderByUpdatedAtDesc(tenantId).stream()
                .map(DeviceControlCollectionsService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DeviceControlDtos.DeviceControlActionLogResponse> listActions(String tenantId) {
        return actionLogRepository.findAllByTenantIdOrderByCreatedAtDesc(tenantId).stream()
                .map(DeviceControlCollectionsService::toResponse)
                .toList();
    }

    @Transactional
    public DeviceControlDtos.DirectUnlockResponse unlockByImei(
            String tenantId,
            DeviceControlDtos.DirectUnlockRequest request,
            String actor) {
        TenantDeviceControlConfig config = configService.requireConfig(tenantId);
        if (!config.isEnabled() || !config.isUnlockEnabled()) {
            throw new BadRequestException("Device unlock actions are not enabled for this tenant");
        }

        String imei1 = request.imei1().trim();
        if (imei1.isBlank()) {
            throw new BadRequestException("imei1 is required");
        }
        if (imei1.length() > 100) {
            throw new BadRequestException("imei1 is too long");
        }
        if (stateRepository.findByTenantIdAndImei2(tenantId, imei1).isPresent()) {
            throw new BadRequestException("Only IMEI 1 is supported for unlock requests");
        }

        LoanDeviceControlState state = stateRepository.findByTenantIdAndImei1(tenantId, imei1)
                .orElseThrow(() -> new NotFoundException("Device-control state not found for the provided IMEI 1"));
        LoanRequestApplication application = applicationRepository.findByIdAndTenantId(state.getApplicationId(), tenantId)
                .orElseThrow(() -> new NotFoundException("Loan application not found"));

        unlockDevice(config, application, state, DeviceControlTriggerType.MANUAL, "manual-imei-unlock", actor);

        return new DeviceControlDtos.DirectUnlockResponse(
                state.getCurrentState() == LoanDeviceControlCurrentState.CLEAR
                        ? "Device unlock request completed"
                        : "Device unlock request submitted",
                state.getImei1(),
                state.getApplicationId(),
                state.getFineractLoanId(),
                state.getLastProviderReference(),
                Instant.now());
    }

    @Transactional(readOnly = true)
    public DeviceControlDtos.OfflinePinResponse getOfflinePin(String tenantId, String applicationId, DeviceControlDtos.OfflinePinRequest request, String actor) {
        LoanRequestApplication application = applicationRepository.findByIdAndTenantId(applicationId, tenantId)
                .orElseThrow(() -> new NotFoundException("Loan application not found"));
        TenantDeviceControlConfig config = configService.requireConfig(tenantId);
        if (!config.isEnabled() || !config.isOfflinePinEnabled()) {
            throw new BadRequestException("Offline PIN retrieval is not enabled for this tenant");
        }
        DeviceControlGateway.RuntimeConfig runtimeConfig = configService.getRuntimeConfig(tenantId);
        DeviceControlGateway.OfflinePinResult result = deviceControlGateway.getOfflinePin(runtimeConfig, request.passKey().trim());
        DeviceControlActionLog logEntry = newAction(application, DeviceControlActionType.OFFLINE_PIN, DeviceControlTriggerType.MANUAL, null, null, actor);
        logEntry.setRequestPayload(result.requestPayload());
        logEntry.setResponsePayload(result.responsePayload());
        logEntry.setStatus(DeviceControlActionStatus.SUCCEEDED);
        logEntry.setProviderTransactionId(result.passcode());
        actionLogRepository.save(logEntry);
        return new DeviceControlDtos.OfflinePinResponse(
                result.message() == null ? "Offline unlock PIN generated" : result.message(),
                result.passcode());
    }

    @Transactional
    public void retryAction(String tenantId, String actionId, String actor) {
        DeviceControlActionLog action = actionLogRepository.findById(actionId)
                .orElseThrow(() -> new NotFoundException("Device-control action not found"));
        if (!tenantId.equals(action.getTenantId())) {
            throw new NotFoundException("Device-control action not found");
        }
        if (action.getActionType() == DeviceControlActionType.LOCK) {
            LoanRequestApplication application = applicationRepository.findByIdAndTenantId(action.getApplicationId(), tenantId)
                    .orElseThrow(() -> new NotFoundException("Loan application not found"));
            TenantDeviceControlConfig config = configService.requireConfig(tenantId);
            lockDevice(config, application, ensureState(application), action.getTriggerType(), action.getRequestedBy(), actor);
            return;
        }
        throw new BadRequestException("Only lock retries are supported at the moment");
    }

    private void runOverdueAndPredueSweep(String tenantId, boolean includeLocks, boolean includePreDue) {
        TenantDeviceControlConfig config = configRepository.findByTenantId(tenantId).orElse(null);
        if (config == null || !config.isEnabled()) {
            return;
        }
        Tenant tenant = tenantService.getRequiredTenant(tenantId);
        List<LoanRequestApplication> applications = applicationRepository
                .findAllByTenantIdAndStatusAndAssignedDeviceIdIsNotNullAndAssignedDeviceImei1IsNotNullAndFineractLoanIdIsNotNullOrderByCreatedAtAsc(
                        tenantId,
                        ApplicationStatus.FINERACT_LOAN_ACTIVATED);
        if (applications.isEmpty()) {
            return;
        }
        Map<String, LoanRequestApplication> byLoanId = new HashMap<>();
        for (LoanRequestApplication application : applications) {
            byLoanId.put(application.getFineractLoanId(), application);
        }
        List<PendingBulkLock> pendingLocks = new ArrayList<>();
        Map<String, PendingNotificationBatch> pendingNotifications = new HashMap<>();
        Map<String, PendingNudgeBatch> pendingNudges = new HashMap<>();
        int offset = 0;
        while (true) {
            FineractGateway.LoanPage page = fineractGateway.fetchLoansPage(tenant, offset, FINERACT_PAGE_SIZE);
            for (FineractGateway.LoanPageItem item : page.items()) {
                if (!item.active()) {
                    continue;
                }
                LoanRequestApplication application = byLoanId.get(item.id());
                if (application == null) {
                    continue;
                }
                LoanDeviceControlState state = ensureState(application);
                FineractGateway.LoanCollectionsSnapshot snapshot = fineractGateway.fetchLoanCollectionsSnapshot(tenant, item.id());
                applySnapshot(state, snapshot);
                if (includeLocks && config.isLockEnabled() && snapshot.hasOverdueInstallment()) {
                    queueLock(config, application, state, DeviceControlTriggerType.SCHEDULED_OVERDUE, null, "system", pendingLocks);
                }
                if (includePreDue) {
                    queuePreDueRules(config, application, state, snapshot, pendingNotifications, pendingNudges);
                }
            }
            if (!page.hasNext()) {
                break;
            }
            offset += page.limit();
        }
        flushLocks(config, pendingLocks);
        flushNotifications(config, pendingNotifications);
        flushNudges(config, pendingNudges);
    }

    private void queuePreDueRules(
            TenantDeviceControlConfig config,
            LoanRequestApplication application,
            LoanDeviceControlState state,
            FineractGateway.LoanCollectionsSnapshot snapshot,
            Map<String, PendingNotificationBatch> pendingNotifications,
            Map<String, PendingNudgeBatch> pendingNudges) {
        if (snapshot.nextDueDate() == null) {
            return;
        }
        if (snapshot.hasOverdueInstallment()) {
            return;
        }
        long daysUntilDue = ChronoUnit.DAYS.between(LocalDate.now(), snapshot.nextDueDate());
        if (daysUntilDue < 0) {
            return;
        }
        if (config.isRemindersEnabled()) {
            List<TenantDeviceControlNotificationRule> rules = notificationRuleRepository.findAllByTenantConfigIdAndActiveTrueOrderByDaysBeforeDueAscIdAsc(config.getId());
            for (TenantDeviceControlNotificationRule rule : rules) {
                if (rule.getDaysBeforeDue() != null && rule.getDaysBeforeDue() == daysUntilDue) {
                    queueNotification(config, application, state, rule, snapshot.nextDueDate(), pendingNotifications);
                }
            }
            List<TenantDeviceControlCustomNotificationRule> customRules =
                    customNotificationRuleRepository.findAllByTenantConfigIdAndActiveTrueOrderByDaysBeforeDueAscIdAsc(config.getId());
            for (TenantDeviceControlCustomNotificationRule rule : customRules) {
                if (rule.getDaysBeforeDue() != null && rule.getDaysBeforeDue() == daysUntilDue) {
                    queueCustomNotification(config, application, state, rule, snapshot, snapshot.nextDueDate(), pendingNotifications);
                }
            }
        }
        if (config.isNudgesEnabled()) {
            List<TenantDeviceControlNudgeRule> rules = nudgeRuleRepository.findAllByTenantConfigIdAndActiveTrueOrderByDaysBeforeDueAscIdAsc(config.getId());
            for (TenantDeviceControlNudgeRule rule : rules) {
                if (rule.getDaysBeforeDue() != null && rule.getDaysBeforeDue() == daysUntilDue) {
                    queueNudge(config, application, state, rule, snapshot.nextDueDate(), pendingNudges);
                }
            }
        }
    }

    private void queueLock(
            TenantDeviceControlConfig config,
            LoanRequestApplication application,
            LoanDeviceControlState state,
            DeviceControlTriggerType triggerType,
            String triggerReference,
            String actor,
            List<PendingBulkLock> pendingLocks) {
        if (state.getCurrentState() == LoanDeviceControlCurrentState.LOCKED || state.getCurrentState() == LoanDeviceControlCurrentState.LOCK_PENDING) {
            return;
        }
        DeviceControlActionLog logEntry = newAction(application, DeviceControlActionType.LOCK, triggerType, null, state.getLastDueDate(), actor);
        logEntry.setStatus(DeviceControlActionStatus.PENDING);
        logEntry.setRequestedBy(triggerReference == null ? actor : actor + "|" + triggerReference);
        logEntry = actionLogRepository.save(logEntry);
        state.setCurrentState(LoanDeviceControlCurrentState.LOCK_PENDING);
        updateDeviceLockStatus(state, InventoryDeviceLockStatus.LOCK_PENDING);
        pendingLocks.add(new PendingBulkLock(
                application,
                state,
                logEntry,
                new DeviceControlGateway.BulkActionItem(
                        state.getImei1(),
                        logEntry.getId(),
                        config.getChannelCode(),
                        renderPaymentLink(config, application),
                        Map.of())));
    }

    private void queueUnlock(
            TenantDeviceControlConfig config,
            LoanRequestApplication application,
            LoanDeviceControlState state,
            DeviceControlTriggerType triggerType,
            String triggerReference,
            String actor,
            List<PendingBulkUnlock> pendingUnlocks) {
        DeviceControlActionLog logEntry = newAction(application, DeviceControlActionType.UNLOCK, triggerType, null, state.getNextDueDate(), actor);
        logEntry.setStatus(DeviceControlActionStatus.PENDING);
        logEntry.setRequestedBy(triggerReference == null ? actor : actor + "|" + triggerReference);
        logEntry = actionLogRepository.save(logEntry);
        state.setCurrentState(LoanDeviceControlCurrentState.UNLOCK_PENDING);
        updateDeviceLockStatus(state, InventoryDeviceLockStatus.UNLOCK_PENDING);
        pendingUnlocks.add(new PendingBulkUnlock(
                state,
                logEntry,
                new DeviceControlGateway.BulkActionItem(
                        state.getImei1(),
                        logEntry.getId(),
                        config.getChannelCode(),
                        null,
                        Map.of())));
    }

    private void lockDevice(
            TenantDeviceControlConfig config,
            LoanRequestApplication application,
            LoanDeviceControlState state,
            DeviceControlTriggerType triggerType,
            String triggerReference,
            String actor) {
        DeviceControlActionLog logEntry = newAction(application, DeviceControlActionType.LOCK, triggerType, null, state.getLastDueDate(), actor);
        logEntry.setStatus(DeviceControlActionStatus.PENDING);
        logEntry.setRequestedBy(triggerReference == null ? actor : actor + "|" + triggerReference);
        logEntry = actionLogRepository.save(logEntry);
        updateDeviceLockStatus(state, InventoryDeviceLockStatus.LOCK_PENDING);
        try {
            DeviceControlGateway.RuntimeConfig runtimeConfig = configService.getRuntimeConfig(config.getTenantId());
            String transactionId = UUID.randomUUID().toString();
            DeviceControlGateway.BulkActionResult result = deviceControlGateway.lock(
                    runtimeConfig,
                    transactionId,
                    List.of(new DeviceControlGateway.BulkActionItem(
                            state.getImei1(),
                            logEntry.getId(),
                            config.getChannelCode(),
                            renderPaymentLink(config, application),
                            Map.of())));
            logEntry.setStatus(DeviceControlActionStatus.SUCCEEDED);
            logEntry.setProviderTransactionId(result.transactionId());
            logEntry.setRequestPayload(result.requestPayload());
            logEntry.setResponsePayload(result.responsePayload());
            state.setCurrentState(LoanDeviceControlCurrentState.LOCKED);
            state.setLastLockActionAt(Instant.now());
            state.setLastProviderReference(result.transactionId());
            updateDeviceLockStatus(state, InventoryDeviceLockStatus.LOCKED);
        } catch (Exception ex) {
            logEntry.setStatus(DeviceControlActionStatus.FAILED);
            logEntry.setFailureReason(ex.getMessage());
            state.setCurrentState(LoanDeviceControlCurrentState.ERROR);
            updateDeviceLockStatus(state, InventoryDeviceLockStatus.ERROR);
        }
    }

    private void unlockDevice(TenantDeviceControlConfig config, LoanRequestApplication application, LoanDeviceControlState state, DeviceControlTriggerType triggerType, String triggerReference, String actor) {
        DeviceControlActionLog logEntry = newAction(application, DeviceControlActionType.UNLOCK, triggerType, null, state.getNextDueDate(), actor);
        logEntry.setStatus(DeviceControlActionStatus.PENDING);
        logEntry.setRequestedBy(triggerReference == null ? actor : actor + "|" + triggerReference);
        logEntry = actionLogRepository.save(logEntry);
        updateDeviceLockStatus(state, InventoryDeviceLockStatus.UNLOCK_PENDING);
        try {
            DeviceControlGateway.RuntimeConfig runtimeConfig = configService.getRuntimeConfig(config.getTenantId());
            DeviceControlGateway.ActionResult result = deviceControlGateway.unlock(runtimeConfig, state.getImei1(), logEntry.getId());
            logEntry.setStatus(DeviceControlActionStatus.SUCCEEDED);
            logEntry.setProviderTransactionId(result.providerReference());
            logEntry.setRequestPayload(result.requestPayload());
            logEntry.setResponsePayload(result.responsePayload());
            state.setCurrentState(LoanDeviceControlCurrentState.CLEAR);
            state.setLastUnlockActionAt(Instant.now());
            state.setLastProviderReference(result.providerReference());
            updateDeviceLockStatus(state, InventoryDeviceLockStatus.CLEAR);
        } catch (Exception ex) {
            logEntry.setStatus(DeviceControlActionStatus.FAILED);
            logEntry.setFailureReason(ex.getMessage());
            state.setCurrentState(LoanDeviceControlCurrentState.ERROR);
            updateDeviceLockStatus(state, InventoryDeviceLockStatus.ERROR);
        }
    }

    private void activateAutoLockIfEligible(
            TenantDeviceControlConfig config,
            LoanRequestApplication application,
            LoanDeviceControlState state,
            LocalDate nextDueDate,
            DeviceControlTriggerType triggerType,
            String triggerReference,
            String actor) {
        if (!config.isLockEnabled() || nextDueDate == null) {
            return;
        }
        if (!nextDueDate.isAfter(LocalDate.now(NAIROBI_ZONE))) {
            return;
        }
        if (state.isHasOverdueInstallment()) {
            return;
        }
        if (actionLogRepository.existsByApplicationIdAndDueDateAndActionTypeAndStatusIn(
                application.getId(),
                nextDueDate,
                DeviceControlActionType.AUTO_LOCK_ACTIVATE,
                Set.of(DeviceControlActionStatus.PENDING, DeviceControlActionStatus.SUCCEEDED))) {
            return;
        }

        DeviceControlActionLog logEntry = newAction(
                application,
                DeviceControlActionType.AUTO_LOCK_ACTIVATE,
                triggerType,
                null,
                nextDueDate,
                actor);
        logEntry.setStatus(DeviceControlActionStatus.PENDING);
        logEntry.setRequestedBy(triggerReference == null ? actor : actor + "|" + triggerReference);
        logEntry = actionLogRepository.save(logEntry);

        ZonedDateTime utcDateTime = autoLockDueDateTimeUtc(nextDueDate);
        try {
            DeviceControlGateway.RuntimeConfig runtimeConfig = configService.getRuntimeConfig(config.getTenantId());
            String transactionId = UUID.randomUUID().toString();
            DeviceControlGateway.BulkActionResult result = deviceControlGateway.activateAutoLock(
                    runtimeConfig,
                    transactionId,
                    List.of(new DeviceControlGateway.AutoLockItem(
                            state.getImei1(),
                            utcDateTime.toLocalDate(),
                            utcDateTime.toLocalTime().withSecond(0).withNano(0))));
            logEntry.setStatus(DeviceControlActionStatus.SUCCEEDED);
            logEntry.setProviderTransactionId(result.transactionId());
            logEntry.setRequestPayload(result.requestPayload());
            logEntry.setResponsePayload(result.responsePayload());
            updateDeviceLockStatus(state, InventoryDeviceLockStatus.AUTO_LOCK_ACTIVE);
        } catch (Exception ex) {
            logEntry.setStatus(DeviceControlActionStatus.FAILED);
            logEntry.setFailureReason(ex.getMessage());
            updateDeviceLockStatus(state, InventoryDeviceLockStatus.ERROR);
        }
    }

    private void queueNotification(
            TenantDeviceControlConfig config,
            LoanRequestApplication application,
            LoanDeviceControlState state,
            TenantDeviceControlNotificationRule rule,
            LocalDate dueDate,
            Map<String, PendingNotificationBatch> pendingNotifications) {
        if (actionLogRepository.existsByRuleIdAndApplicationIdAndDueDateAndActionTypeAndStatusIn(
                rule.getId(),
                application.getId(),
                dueDate,
                DeviceControlActionType.NOTIFICATION,
                Set.of(DeviceControlActionStatus.PENDING, DeviceControlActionStatus.SUCCEEDED))) {
            return;
        }
        DeviceControlActionLog logEntry = newAction(
                application,
                DeviceControlActionType.NOTIFICATION,
                DeviceControlTriggerType.SCHEDULED_PRE_DUE,
                rule.getId(),
                dueDate,
                "system");
        logEntry.setRuleDayOffset(rule.getDaysBeforeDue());
        logEntry.setStatus(DeviceControlActionStatus.PENDING);
        logEntry = actionLogRepository.save(logEntry);
        PendingNotificationBatch batch = pendingNotifications.computeIfAbsent(rule.getId(),
                ignored -> new PendingNotificationBatch(rule.getNotificationCode(), new ArrayList<>()));
        batch.items().add(new PendingNotification(
                state,
                logEntry,
                new DeviceControlGateway.BulkActionItem(state.getImei1(), logEntry.getId(), config.getChannelCode(), null, Map.of())));
    }

    private void queueCustomNotification(
            TenantDeviceControlConfig config,
            LoanRequestApplication application,
            LoanDeviceControlState state,
            TenantDeviceControlCustomNotificationRule rule,
            FineractGateway.LoanCollectionsSnapshot snapshot,
            LocalDate dueDate,
            Map<String, PendingNotificationBatch> pendingNotifications) {
        if (actionLogRepository.existsByRuleIdAndApplicationIdAndDueDateAndActionTypeAndStatusIn(
                rule.getId(),
                application.getId(),
                dueDate,
                DeviceControlActionType.NOTIFICATION,
                Set.of(DeviceControlActionStatus.PENDING, DeviceControlActionStatus.SUCCEEDED))) {
            return;
        }
        List<TenantDeviceControlCustomNotificationRuleField> mappings =
                customNotificationRuleFieldRepository.findAllByCustomRuleIdOrderByDisplayOrderAscIdAsc(rule.getId());
        if (mappings.isEmpty()) {
            return;
        }
        DeviceControlActionLog logEntry = newAction(
                application,
                DeviceControlActionType.NOTIFICATION,
                DeviceControlTriggerType.SCHEDULED_PRE_DUE,
                rule.getId(),
                dueDate,
                "system");
        logEntry.setRuleDayOffset(rule.getDaysBeforeDue());
        logEntry.setStatus(DeviceControlActionStatus.PENDING);
        logEntry = actionLogRepository.save(logEntry);
        PendingNotificationBatch batch = pendingNotifications.computeIfAbsent(rule.getId(),
                ignored -> new PendingNotificationBatch(rule.getNotificationCode(), new ArrayList<>()));
        batch.items().add(new PendingNotification(
                state,
                logEntry,
                new DeviceControlGateway.BulkActionItem(
                        state.getImei1(),
                        logEntry.getId(),
                        config.getChannelCode(),
                        null,
                        buildCustomNotificationFields(config, application, state, snapshot, dueDate, mappings))));
    }

    private void queueNudge(
            TenantDeviceControlConfig config,
            LoanRequestApplication application,
            LoanDeviceControlState state,
            TenantDeviceControlNudgeRule rule,
            LocalDate dueDate,
            Map<String, PendingNudgeBatch> pendingNudges) {
        if (actionLogRepository.existsByRuleIdAndApplicationIdAndDueDateAndActionTypeAndStatusIn(
                rule.getId(),
                application.getId(),
                dueDate,
                DeviceControlActionType.NUDGE,
                Set.of(DeviceControlActionStatus.PENDING, DeviceControlActionStatus.SUCCEEDED))) {
            return;
        }
        DeviceControlActionLog logEntry = newAction(
                application,
                DeviceControlActionType.NUDGE,
                DeviceControlTriggerType.SCHEDULED_PRE_DUE,
                rule.getId(),
                dueDate,
                "system");
        logEntry.setRuleDayOffset(rule.getDaysBeforeDue());
        logEntry.setStatus(DeviceControlActionStatus.PENDING);
        logEntry = actionLogRepository.save(logEntry);
        PendingNudgeBatch batch = pendingNudges.computeIfAbsent(rule.getId(), ignored -> new PendingNudgeBatch(new ArrayList<>()));
        batch.items().add(new PendingNudge(
                state,
                logEntry,
                new DeviceControlGateway.BulkActionItem(state.getImei1(), logEntry.getId(), config.getChannelCode(), null, Map.of())));
    }

    private BulkFlushSummary flushLocks(TenantDeviceControlConfig config, List<PendingBulkLock> pendingLocks) {
        if (pendingLocks.isEmpty()) {
            return new BulkFlushSummary(0, 0, 0, null);
        }
        String transactionId = UUID.randomUUID().toString();
        try {
            DeviceControlGateway.RuntimeConfig runtimeConfig = configService.getRuntimeConfig(config.getTenantId());
            List<DeviceControlGateway.BulkActionItem> items = pendingLocks.stream().map(PendingBulkLock::item).toList();
            DeviceControlGateway.BulkActionResult result = deviceControlGateway.lock(runtimeConfig, transactionId, items);
            Instant now = Instant.now();
            for (PendingBulkLock pending : pendingLocks) {
                pending.logEntry().setStatus(DeviceControlActionStatus.SUCCEEDED);
                pending.logEntry().setProviderTransactionId(result.transactionId());
                pending.logEntry().setRequestPayload(result.requestPayload());
                pending.logEntry().setResponsePayload(result.responsePayload());
                pending.state().setCurrentState(LoanDeviceControlCurrentState.LOCKED);
                pending.state().setLastLockActionAt(now);
                pending.state().setLastProviderReference(result.transactionId());
                updateDeviceLockStatus(pending.state(), InventoryDeviceLockStatus.LOCKED);
            }
            return new BulkFlushSummary(pendingLocks.size(), pendingLocks.size(), 0, result.transactionId());
        } catch (Exception ex) {
            for (PendingBulkLock pending : pendingLocks) {
                pending.logEntry().setStatus(DeviceControlActionStatus.FAILED);
                pending.logEntry().setFailureReason(ex.getMessage());
                pending.state().setCurrentState(LoanDeviceControlCurrentState.ERROR);
                updateDeviceLockStatus(pending.state(), InventoryDeviceLockStatus.ERROR);
            }
            return new BulkFlushSummary(pendingLocks.size(), 0, pendingLocks.size(), transactionId);
        }
    }

    private BulkFlushSummary flushUnlocks(TenantDeviceControlConfig config, List<PendingBulkUnlock> pendingUnlocks) {
        if (pendingUnlocks.isEmpty()) {
            return new BulkFlushSummary(0, 0, 0, null);
        }
        String transactionId = UUID.randomUUID().toString();
        try {
            DeviceControlGateway.RuntimeConfig runtimeConfig = configService.getRuntimeConfig(config.getTenantId());
            List<DeviceControlGateway.BulkActionItem> items = pendingUnlocks.stream().map(PendingBulkUnlock::item).toList();
            DeviceControlGateway.BulkActionResult result = deviceControlGateway.bulkUnlock(runtimeConfig, transactionId, items);
            Instant now = Instant.now();
            for (PendingBulkUnlock pending : pendingUnlocks) {
                pending.logEntry().setStatus(DeviceControlActionStatus.SUCCEEDED);
                pending.logEntry().setProviderTransactionId(result.transactionId());
                pending.logEntry().setRequestPayload(result.requestPayload());
                pending.logEntry().setResponsePayload(result.responsePayload());
                pending.state().setCurrentState(LoanDeviceControlCurrentState.CLEAR);
                pending.state().setLastUnlockActionAt(now);
                pending.state().setLastProviderReference(result.transactionId());
                updateDeviceLockStatus(pending.state(), InventoryDeviceLockStatus.CLEAR);
            }
            return new BulkFlushSummary(pendingUnlocks.size(), pendingUnlocks.size(), 0, result.transactionId());
        } catch (Exception ex) {
            for (PendingBulkUnlock pending : pendingUnlocks) {
                pending.logEntry().setStatus(DeviceControlActionStatus.FAILED);
                pending.logEntry().setFailureReason(ex.getMessage());
                pending.state().setCurrentState(LoanDeviceControlCurrentState.ERROR);
                updateDeviceLockStatus(pending.state(), InventoryDeviceLockStatus.ERROR);
            }
            return new BulkFlushSummary(pendingUnlocks.size(), 0, pendingUnlocks.size(), transactionId);
        }
    }

    private void flushNotifications(TenantDeviceControlConfig config, Map<String, PendingNotificationBatch> pendingNotifications) {
        if (pendingNotifications.isEmpty()) {
            return;
        }
        DeviceControlGateway.RuntimeConfig runtimeConfig = configService.getRuntimeConfig(config.getTenantId());
        for (PendingNotificationBatch batch : pendingNotifications.values()) {
            String transactionId = UUID.randomUUID().toString();
            try {
                List<DeviceControlGateway.BulkActionItem> items = batch.items().stream().map(PendingNotification::item).toList();
                DeviceControlGateway.BulkActionResult result = deviceControlGateway.sendNotification(
                        runtimeConfig,
                        transactionId,
                        batch.notificationCode(),
                        items);
                Instant now = Instant.now();
                for (PendingNotification pending : batch.items()) {
                    pending.logEntry().setStatus(DeviceControlActionStatus.SUCCEEDED);
                    pending.logEntry().setProviderTransactionId(result.transactionId());
                    pending.logEntry().setRequestPayload(result.requestPayload());
                    pending.logEntry().setResponsePayload(result.responsePayload());
                    pending.state().setLastNotificationActionAt(now);
                }
            } catch (Exception ex) {
                for (PendingNotification pending : batch.items()) {
                    pending.logEntry().setStatus(DeviceControlActionStatus.FAILED);
                    pending.logEntry().setFailureReason(ex.getMessage());
                }
            }
        }
    }

    private void flushNudges(TenantDeviceControlConfig config, Map<String, PendingNudgeBatch> pendingNudges) {
        if (pendingNudges.isEmpty()) {
            return;
        }
        DeviceControlGateway.RuntimeConfig runtimeConfig = configService.getRuntimeConfig(config.getTenantId());
        for (PendingNudgeBatch batch : pendingNudges.values()) {
            String transactionId = UUID.randomUUID().toString();
            try {
                List<DeviceControlGateway.BulkActionItem> items = batch.items().stream().map(PendingNudge::item).toList();
                DeviceControlGateway.BulkActionResult result = deviceControlGateway.sendNudge(
                        runtimeConfig,
                        transactionId,
                        items);
                Instant now = Instant.now();
                for (PendingNudge pending : batch.items()) {
                    pending.logEntry().setStatus(DeviceControlActionStatus.SUCCEEDED);
                    pending.logEntry().setProviderTransactionId(result.transactionId());
                    pending.logEntry().setRequestPayload(result.requestPayload());
                    pending.logEntry().setResponsePayload(result.responsePayload());
                    pending.state().setLastNudgeActionAt(now);
                }
            } catch (Exception ex) {
                for (PendingNudge pending : batch.items()) {
                    pending.logEntry().setStatus(DeviceControlActionStatus.FAILED);
                    pending.logEntry().setFailureReason(ex.getMessage());
                }
            }
        }
    }

    private LoanDeviceControlState ensureState(LoanRequestApplication application) {
        return stateRepository.findByApplicationId(application.getId()).orElseGet(() -> {
            LoanDeviceControlState state = new LoanDeviceControlState();
            state.setTenantId(application.getTenantId());
            state.setApplicationId(application.getId());
            state.setFineractLoanId(application.getFineractLoanId());
            state.setDeviceId(application.getAssignedDeviceId());
            state.setImei1(application.getAssignedDeviceImei1());
            state.setImei2(application.getAssignedDeviceImei2());
            return stateRepository.save(state);
        });
    }

    private void applySnapshot(LoanDeviceControlState state, FineractGateway.LoanCollectionsSnapshot snapshot) {
        state.setHasOverdueInstallment(snapshot.hasOverdueInstallment());
        state.setDaysOverdue(snapshot.daysOverdue());
        state.setLastDueDate(snapshot.oldestOverdueDate());
        state.setNextDueDate(snapshot.nextDueDate());
        state.setLastCollectionsEvaluatedAt(Instant.now());
    }

    private DeviceControlActionLog newAction(
            LoanRequestApplication application,
            DeviceControlActionType actionType,
            DeviceControlTriggerType triggerType,
            String ruleId,
            LocalDate dueDate,
            String actor) {
        DeviceControlActionLog action = new DeviceControlActionLog();
        action.setTenantId(application.getTenantId());
        action.setApplicationId(application.getId());
        action.setFineractLoanId(application.getFineractLoanId());
        action.setDeviceId(application.getAssignedDeviceId());
        action.setImei1(application.getAssignedDeviceImei1());
        action.setActionType(actionType);
        action.setTriggerType(triggerType);
        action.setRuleId(ruleId);
        action.setDueDate(dueDate);
        action.setRequestedBy(actor);
        return action;
    }

    private ZonedDateTime autoLockDueDateTimeUtc(LocalDate nextDueDate) {
        return ZonedDateTime.of(nextDueDate, AUTO_LOCK_TIME_NAIROBI, NAIROBI_ZONE)
                .withZoneSameInstant(ZoneOffset.UTC);
    }

    private void updateDeviceLockStatus(LoanDeviceControlState state, InventoryDeviceLockStatus lockStatus) {
        if (state.getDeviceId() == null) {
            return;
        }
        InventoryDevice device = inventoryDeviceRepository.findByIdAndTenantId(state.getDeviceId(), state.getTenantId())
                .orElse(null);
        if (device == null) {
            return;
        }
        device.setLockStatus(lockStatus);
    }

    private Map<String, String> buildCustomNotificationFields(
            TenantDeviceControlConfig config,
            LoanRequestApplication application,
            LoanDeviceControlState state,
            FineractGateway.LoanCollectionsSnapshot snapshot,
            LocalDate dueDate,
            List<TenantDeviceControlCustomNotificationRuleField> mappings) {
        Map<String, String> fields = new LinkedHashMap<>();
        java.math.BigDecimal dueAmount = resolveDueAmount(snapshot, dueDate, application);
        for (TenantDeviceControlCustomNotificationRuleField mapping : mappings) {
            fields.put(mapping.getColumnName(), resolveCustomFieldValue(config, application, state, dueDate, dueAmount, mapping.getSourceField()));
        }
        return fields;
    }

    private String resolveCustomFieldValue(
            TenantDeviceControlConfig config,
            LoanRequestApplication application,
            LoanDeviceControlState state,
            LocalDate dueDate,
            java.math.BigDecimal dueAmount,
            CustomNotificationSourceField sourceField) {
        return switch (sourceField) {
            case CUSTOMER_NAME -> joinName(application.getApplicantFirstName(), application.getApplicantLastName());
            case EMI_AMOUNT -> decimalText(application.getInstallmentAmount());
            case DUE_DATE -> dueDate == null ? null : dueDate.toString();
            case TOTAL_TO_BE_PAID -> decimalText(dueAmount);
            case ACCOUNT_NUMBER -> application.getFineractLoanId();
            case CUSTOMER_MOBILE -> application.getPhoneNumber();
            case DAYS_PAST_DUE -> String.valueOf(state.getDaysOverdue() == null ? 0L : state.getDaysOverdue());
            case CHANNEL_CODE -> config.getChannelCode();
        };
    }

    private java.math.BigDecimal resolveDueAmount(
            FineractGateway.LoanCollectionsSnapshot snapshot,
            LocalDate dueDate,
            LoanRequestApplication application) {
        if (snapshot != null && snapshot.installments() != null && dueDate != null) {
            for (FineractGateway.InstallmentSnapshot installment : snapshot.installments()) {
                if (dueDate.equals(installment.dueDate()) && installment.outstandingAmount() != null) {
                    return installment.outstandingAmount();
                }
            }
        }
        return application.getInstallmentAmount();
    }

    private String decimalText(java.math.BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private String joinName(String firstName, String lastName) {
        String first = firstName == null ? "" : firstName.trim();
        String last = lastName == null ? "" : lastName.trim();
        String value = (first + " " + last).trim();
        return value.isEmpty() ? null : value;
    }

    private boolean canQueueUnlock(LoanDeviceControlState state) {
        return state.getCurrentState() == LoanDeviceControlCurrentState.LOCKED
                || state.getCurrentState() == LoanDeviceControlCurrentState.LOCK_PENDING
                || state.getCurrentState() == LoanDeviceControlCurrentState.UNLOCK_PENDING;
    }

    private boolean shouldRun(Instant lastRunAt, Integer cadenceMinutes, Instant now) {
        if (cadenceMinutes == null || cadenceMinutes <= 0) {
            return false;
        }
        if (lastRunAt == null) {
            return true;
        }
        return lastRunAt.plus(cadenceMinutes, ChronoUnit.MINUTES).isBefore(now);
    }

    private String renderPaymentLink(TenantDeviceControlConfig config, LoanRequestApplication application) {
        if (config.getPaymentLinkTemplate() == null || config.getPaymentLinkTemplate().isBlank()) {
            return null;
        }
        return config.getPaymentLinkTemplate()
                .replace("{applicationId}", application.getId())
                .replace("{fineractLoanId}", application.getFineractLoanId() == null ? "" : application.getFineractLoanId());
    }

    static DeviceControlDtos.LoanDeviceControlStateResponse toResponse(LoanDeviceControlState state) {
        return new DeviceControlDtos.LoanDeviceControlStateResponse(
                state.getId(),
                state.getApplicationId(),
                state.getFineractLoanId(),
                state.getDeviceId(),
                state.getImei1(),
                state.getImei2(),
                state.getCurrentState(),
                state.getLastDueDate(),
                state.getNextDueDate(),
                state.isHasOverdueInstallment(),
                state.getDaysOverdue(),
                state.getLastCollectionsEvaluatedAt(),
                state.getLastLockActionAt(),
                state.getLastUnlockActionAt(),
                state.getLastNotificationActionAt(),
                state.getLastNudgeActionAt(),
                state.getLastProviderReference(),
                state.getCreatedAt(),
                state.getUpdatedAt());
    }

    static DeviceControlDtos.DeviceControlActionLogResponse toResponse(DeviceControlActionLog action) {
        return new DeviceControlDtos.DeviceControlActionLogResponse(
                action.getId(),
                action.getApplicationId(),
                action.getFineractLoanId(),
                action.getDeviceId(),
                action.getImei1(),
                action.getActionType(),
                action.getTriggerType(),
                action.getRuleId(),
                action.getRuleDayOffset(),
                action.getDueDate(),
                action.getProviderTransactionId(),
                action.getStatus(),
                action.getFailureReason(),
                action.getRequestedBy(),
                action.getCreatedAt(),
                action.getUpdatedAt());
    }

    private record PendingBulkLock(
            LoanRequestApplication application,
            LoanDeviceControlState state,
            DeviceControlActionLog logEntry,
            DeviceControlGateway.BulkActionItem item) {
    }

    private record PendingBulkUnlock(
            LoanDeviceControlState state,
            DeviceControlActionLog logEntry,
            DeviceControlGateway.BulkActionItem item) {
    }

    private record BulkFlushSummary(
            int queuedLocks,
            int succeededLocks,
            int failedLocks,
            String providerTransactionId) {
    }

    private record PendingNotificationBatch(
            String notificationCode,
            List<PendingNotification> items) {
    }

    private record PendingNotification(
            LoanDeviceControlState state,
            DeviceControlActionLog logEntry,
            DeviceControlGateway.BulkActionItem item) {
    }

    private record PendingNudgeBatch(
            List<PendingNudge> items) {
    }

    private record PendingNudge(
            LoanDeviceControlState state,
            DeviceControlActionLog logEntry,
            DeviceControlGateway.BulkActionItem item) {
    }
}
