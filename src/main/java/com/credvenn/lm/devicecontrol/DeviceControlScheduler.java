package com.credvenn.lm.devicecontrol;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DeviceControlScheduler {

    private final DeviceControlCollectionsService collectionsService;
    private final DeviceControlSchedulingProperties schedulingProperties;

    public DeviceControlScheduler(
            DeviceControlCollectionsService collectionsService,
            DeviceControlSchedulingProperties schedulingProperties) {
        this.collectionsService = collectionsService;
        this.schedulingProperties = schedulingProperties;
    }

    @Scheduled(cron = "0 5 0 * * *", zone = "Africa/Nairobi")
    public void runDailyOverdueLockSweeps() {
        if (!schedulingProperties.isEnabled() || !schedulingProperties.isDailyOverdueLocksEnabled()) {
            return;
        }
        collectionsService.runDailyOverdueLockSweeps();
    }

    @Scheduled(cron = "0 20 0 * * *", zone = "Africa/Nairobi")
    public void runDailyUnlockSweeps() {
        if (!schedulingProperties.isEnabled() || !schedulingProperties.isDailyUnlocksEnabled()) {
            return;
        }
        collectionsService.runDailyUnlockSweeps();
    }

    @Scheduled(fixedDelay = 17280000)
    public void runTenantCollectionsSweeps() {
        if (!schedulingProperties.isEnabled() || !schedulingProperties.isTenantSweepsEnabled()) {
            return;
        }
        collectionsService.runScheduledSweeps();
    }
}
