package com.credvenn.lm.devicecontrol;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantDeviceControlCustomNotificationRuleRepository extends JpaRepository<TenantDeviceControlCustomNotificationRule, String> {
    List<TenantDeviceControlCustomNotificationRule> findAllByTenantConfigIdOrderByDaysBeforeDueAscIdAsc(String tenantConfigId);
    List<TenantDeviceControlCustomNotificationRule> findAllByTenantConfigIdAndActiveTrueOrderByDaysBeforeDueAscIdAsc(String tenantConfigId);
    Optional<TenantDeviceControlCustomNotificationRule> findByIdAndTenantConfigId(String id, String tenantConfigId);
}
