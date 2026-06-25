package com.credvenn.lm.loanproduct;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanProductMappingRepository extends JpaRepository<LoanProductMapping, String> {

    boolean existsByTenantIdAndProductCodeIgnoreCase(String tenantId, String productCode);

    Optional<LoanProductMapping> findByTenantIdAndProductCodeIgnoreCase(String tenantId, String productCode);

    Optional<LoanProductMapping> findByTenantIdAndShortNameIgnoreCase(String tenantId, String shortName);

    Page<LoanProductMapping> findAllByTenantIdAndActiveTrue(String tenantId, Pageable pageable);

    List<LoanProductMapping> findAllByTenantIdAndActiveTrueOrderByDisplayNameAsc(String tenantId);
}
