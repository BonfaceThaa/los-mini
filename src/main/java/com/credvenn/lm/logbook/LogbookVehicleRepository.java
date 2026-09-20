package com.credvenn.lm.logbook;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface LogbookVehicleRepository extends JpaRepository<LogbookVehicle,String> {
    Optional<LogbookVehicle> findByTenantIdAndApplicationId(String tenantId, String applicationId);
}
