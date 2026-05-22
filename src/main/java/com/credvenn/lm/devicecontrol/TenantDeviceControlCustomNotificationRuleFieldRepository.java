package com.credvenn.lm.devicecontrol;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantDeviceControlCustomNotificationRuleFieldRepository extends JpaRepository<TenantDeviceControlCustomNotificationRuleField, String> {
    List<TenantDeviceControlCustomNotificationRuleField> findAllByCustomRuleIdOrderByDisplayOrderAscIdAsc(String customRuleId);
    void deleteAllByCustomRuleId(String customRuleId);
}
