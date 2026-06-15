package com.credvenn.lm.inventory;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryDeviceRepository extends JpaRepository<InventoryDevice, String> {

    List<InventoryDevice> findAllByTenantIdOrderByDeviceNameAsc(String tenantId);

    Page<InventoryDevice> findAllByTenantId(String tenantId, Pageable pageable);

    Optional<InventoryDevice> findByIdAndTenantId(String id, String tenantId);
}
