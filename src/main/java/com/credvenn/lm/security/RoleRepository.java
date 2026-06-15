package com.credvenn.lm.security;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, String> {

    boolean existsByTenantIdAndCodeIgnoreCase(String tenantId, String code);

    Optional<Role> findByCodeIgnoreCaseAndTenantIdIsNull(String code);

    Optional<Role> findByCodeIgnoreCaseAndTenantId(String code, String tenantId);

    @EntityGraph(attributePaths = "permissions")
    List<Role> findAllByTenantIdOrderByNameAsc(String tenantId);

    @EntityGraph(attributePaths = "permissions")
    Page<Role> findAllByTenantId(String tenantId, Pageable pageable);

    @EntityGraph(attributePaths = "permissions")
    List<Role> findAllByTenantIdAndIdIn(String tenantId, Collection<String> ids);

    @EntityGraph(attributePaths = "permissions")
    Optional<Role> findByIdAndTenantId(String id, String tenantId);

    @EntityGraph(attributePaths = "permissions")
    List<Role> findAllByTenantIdIsNullOrderByNameAsc();

    @EntityGraph(attributePaths = "permissions")
    Optional<Role> findByIdAndTenantIdIsNull(String id);
}
