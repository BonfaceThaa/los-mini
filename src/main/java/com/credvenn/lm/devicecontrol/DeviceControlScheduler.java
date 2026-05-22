package com.credvenn.lm.devicecontrol;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DeviceControlScheduler {

    private final DeviceControlCollectionsService collectionsService;

    public DeviceControlScheduler(DeviceControlCollectionsService collectionsService) {
        this.collectionsService = collectionsService;
    }

    @Scheduled(fixedDelay = 60000)
    public void runTenantCollectionsSweeps() {
        collectionsService.runScheduledSweeps();
    }
}
