package com.credvenn.lm.statement;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CladfyStatusPollingScheduler {

    private final CladfyStatusPollingService pollingService;

    public CladfyStatusPollingScheduler(CladfyStatusPollingService pollingService) {
        this.pollingService = pollingService;
    }

    @Scheduled(cron = "0 */5 8-23 * * 1-6", zone = "Africa/Nairobi")
    public void pollDueStatuses() {
        pollingService.pollDueAnalyses();
    }
}
