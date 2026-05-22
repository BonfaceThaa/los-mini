package com.credvenn.lm.devicecontrol;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantDeviceControlNotificationRuleRepository extends JpaRepository<TenantDeviceControlNotificationRule, String> {
    List<TenantDeviceControlNotificationRule> findAllByTenantConfigIdOrderByDaysBeforeDueAscIdAsc(String tenantConfigId);
    List<TenantDeviceControlNotificationRule> findAllByTenantConfigIdAndActiveTrueOrderByDaysBeforeDueAscIdAsc(String tenantConfigId);
    Optional<TenantDeviceControlNotificationRule> findByIdAndTenantConfigId(String id, String tenantConfigId);
}
