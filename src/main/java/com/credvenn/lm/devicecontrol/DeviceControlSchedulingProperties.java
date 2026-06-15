package com.credvenn.lm.devicecontrol;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.device-control.scheduling")
public class DeviceControlSchedulingProperties {

    private boolean enabled = true;
    private boolean tenantSweepsEnabled = true;
    private boolean dailyOverdueLocksEnabled = true;
    private boolean dailyUnlocksEnabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isTenantSweepsEnabled() {
        return tenantSweepsEnabled;
    }

    public void setTenantSweepsEnabled(boolean tenantSweepsEnabled) {
        this.tenantSweepsEnabled = tenantSweepsEnabled;
    }

    public boolean isDailyOverdueLocksEnabled() {
        return dailyOverdueLocksEnabled;
    }

    public void setDailyOverdueLocksEnabled(boolean dailyOverdueLocksEnabled) {
        this.dailyOverdueLocksEnabled = dailyOverdueLocksEnabled;
    }

    public boolean isDailyUnlocksEnabled() {
        return dailyUnlocksEnabled;
    }

    public void setDailyUnlocksEnabled(boolean dailyUnlocksEnabled) {
        this.dailyUnlocksEnabled = dailyUnlocksEnabled;
    }
}
