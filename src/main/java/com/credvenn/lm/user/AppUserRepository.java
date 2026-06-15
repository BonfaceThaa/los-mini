package com.credvenn.lm.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppUserRepository extends JpaRepository<AppUser, String> {

    boolean existsByTenantIdAndUsernameIgnoreCase(String tenantId, String username);

    boolean existsByTenantIdAndEmailIgnoreCase(String tenantId, String email);

    boolean existsByTenantIdIsNullAndUsernameIgnoreCase(String username);

    boolean existsByTenantIdIsNullAndEmailIgnoreCase(String email);

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    @Query("""
            select u from AppUser u
            where u.tenantId = :tenantId
              and (lower(u.username) = lower(:identifier) or lower(u.email) = lower(:identifier))
            """)
    Optional<AppUser> findTenantUserForLogin(@Param("tenantId") String tenantId, @Param("identifier") String identifier);

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    @Query("""
            select u from AppUser u
            where u.tenantId is null
              and (lower(u.username) = lower(:identifier) or lower(u.email) = lower(:identifier))
            """)
    Optional<AppUser> findPlatformUserForLogin(@Param("identifier") String identifier);

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    List<AppUser> findAllByTenantIdOrderByUsernameAsc(String tenantId);

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Page<AppUser> findAllByTenantId(String tenantId, Pageable pageable);

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<AppUser> findByIdAndTenantId(String id, String tenantId);

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    List<AppUser> findAllByTenantIdIsNullOrderByUsernameAsc();

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<AppUser> findByIdAndTenantIdIsNull(String id);

    long countByTenantIdAndActiveTrue(String tenantId);
}
