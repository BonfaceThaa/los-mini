package com.credvenn.lm.applicationvariable;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApplicationVariableDefinitionRepository extends JpaRepository<ApplicationVariableDefinition, String> {
    List<ApplicationVariableDefinition> findAllByTenantIdOrderByDisplayOrderAsc(String tenantId);
    List<ApplicationVariableDefinition> findAllByTenantIdAndActiveTrueOrderByDisplayOrderAsc(String tenantId);
    Optional<ApplicationVariableDefinition> findByIdAndTenantId(String id, String tenantId);
    boolean existsByTenantIdAndCodeIgnoreCase(String tenantId, String code);

    @Query("""
            select d from ApplicationVariableDefinition d
            where d.tenantId = :tenantId and (d.originationProfileId is null or d.originationProfileId = :profileId)
              and (:activeOnly = false or d.active = true)
            order by d.displayOrder, d.code
            """)
    List<ApplicationVariableDefinition> findApplicable(@Param("tenantId") String tenantId,
            @Param("profileId") String profileId, @Param("activeOnly") boolean activeOnly);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
            select d from ApplicationVariableDefinition d
            where d.tenantId = :tenantId and (d.originationProfileId is null or d.originationProfileId = :profileId)
              and d.active = true
            order by d.displayOrder, d.code
            """)
    List<ApplicationVariableDefinition> findApplicableForCreation(@Param("tenantId") String tenantId,
            @Param("profileId") String profileId);
}
