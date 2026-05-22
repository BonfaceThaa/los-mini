package com.credvenn.lm.devicecontrol;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantDeviceControlNudgeRuleRepository extends JpaRepository<TenantDeviceControlNudgeRule, String> {
    List<TenantDeviceControlNudgeRule> findAllByTenantConfigIdOrderByDaysBeforeDueAscIdAsc(String tenantConfigId);
    List<TenantDeviceControlNudgeRule> findAllByTenantConfigIdAndActiveTrueOrderByDaysBeforeDueAscIdAsc(String tenantConfigId);
    Optional<TenantDeviceControlNudgeRule> findByIdAndTenantConfigId(String id, String tenantConfigId);
}
