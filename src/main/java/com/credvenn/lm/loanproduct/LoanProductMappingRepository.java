package com.credvenn.lm.loanproduct;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanProductMappingRepository extends JpaRepository<LoanProductMapping, String> {

    Page<LoanProductMapping> findAllByTenantIdAndOriginationProfileId(String tenantId, String profileId, Pageable pageable);
    Page<LoanProductMapping> findAllByTenantIdAndOriginationProfileIdAndActiveTrue(String tenantId, String profileId, Pageable pageable);
    List<LoanProductMapping> findAllByTenantIdAndOriginationProfileIdAndActiveTrueOrderByDisplayNameAsc(String tenantId, String profileId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<LoanProductMapping> findForUpdateByTenantIdAndId(String tenantId, String id);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<LoanProductMapping> findForUpdateByTenantIdAndShortNameIgnoreCase(String tenantId, String shortName);

    boolean existsByTenantIdAndProductCodeIgnoreCase(String tenantId, String productCode);

    Optional<LoanProductMapping> findByTenantIdAndProductCodeIgnoreCase(String tenantId, String productCode);

    Optional<LoanProductMapping> findByTenantIdAndShortNameIgnoreCase(String tenantId, String shortName);

    Page<LoanProductMapping> findAllByTenantId(String tenantId, Pageable pageable);

    Page<LoanProductMapping> findAllByTenantIdAndActiveTrue(String tenantId, Pageable pageable);

    List<LoanProductMapping> findAllByTenantIdAndActiveTrueOrderByDisplayNameAsc(String tenantId);
}

