package com.credvenn.lm.inventory;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryDeviceAssignmentRepository extends JpaRepository<InventoryDeviceAssignment, String> {

    Optional<InventoryDeviceAssignment> findByApplicationId(String applicationId);
}
