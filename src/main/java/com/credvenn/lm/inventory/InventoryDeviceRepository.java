package com.credvenn.lm.inventory;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryDeviceRepository extends JpaRepository<InventoryDevice, String> {

    List<InventoryDevice> findAllByTenantIdOrderByDeviceNameAsc(String tenantId);

    Optional<InventoryDevice> findByIdAndTenantId(String id, String tenantId);
}
