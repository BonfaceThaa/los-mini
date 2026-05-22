package com.credvenn.lm.loanproduct;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanProductMappingRepository extends JpaRepository<LoanProductMapping, String> {

    boolean existsByTenantIdAndProductCodeIgnoreCase(String tenantId, String productCode);

    Optional<LoanProductMapping> findByTenantIdAndProductCodeIgnoreCase(String tenantId, String productCode);
}
