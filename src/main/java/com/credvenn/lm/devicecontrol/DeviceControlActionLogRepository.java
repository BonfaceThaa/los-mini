package com.credvenn.lm.devicecontrol;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceControlActionLogRepository extends JpaRepository<DeviceControlActionLog, String> {
    List<DeviceControlActionLog> findAllByTenantIdOrderByCreatedAtDesc(String tenantId);
    boolean existsByRuleIdAndApplicationIdAndDueDateAndActionTypeAndStatusIn(
            String ruleId,
            String applicationId,
            LocalDate dueDate,
            DeviceControlActionType actionType,
            Collection<DeviceControlActionStatus> statuses);
}
