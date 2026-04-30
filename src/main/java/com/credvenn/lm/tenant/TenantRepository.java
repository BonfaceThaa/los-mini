package com.credvenn.lm.tenant;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<Tenant, String> {

    boolean existsByCodeIgnoreCase(String code);

    Optional<Tenant> findByCodeIgnoreCase(String code);

    List<Tenant> findAllByOrderByNameAsc();
}
