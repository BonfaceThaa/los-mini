package com.credvenn.lm.devicecontrol;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantDeviceControlConfigRepository extends JpaRepository<TenantDeviceControlConfig, String> {
    Optional<TenantDeviceControlConfig> findByTenantId(String tenantId);
    List<TenantDeviceControlConfig> findAllByEnabledTrueOrderByTenantIdAsc();
}
