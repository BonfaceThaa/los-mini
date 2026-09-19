package com.credvenn.lm.tenant;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<Tenant, String> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select t from Tenant t where t.id = :tenantId")
    Optional<Tenant> findForOriginationUpdate(@org.springframework.data.repository.query.Param("tenantId") String tenantId);

    boolean existsByCodeIgnoreCase(String code);

    Optional<Tenant> findByCodeIgnoreCase(String code);

    List<Tenant> findAllByOrderByNameAsc();
}
