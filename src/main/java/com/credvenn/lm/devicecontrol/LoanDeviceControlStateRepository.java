package com.credvenn.lm.devicecontrol;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanDeviceControlStateRepository extends JpaRepository<LoanDeviceControlState, String> {
    Optional<LoanDeviceControlState> findByApplicationId(String applicationId);
    Optional<LoanDeviceControlState> findByTenantIdAndFineractLoanId(String tenantId, String fineractLoanId);
    List<LoanDeviceControlState> findAllByTenantIdOrderByUpdatedAtDesc(String tenantId);
}
