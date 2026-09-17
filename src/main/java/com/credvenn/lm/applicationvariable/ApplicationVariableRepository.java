package com.credvenn.lm.applicationvariable;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationVariableRepository extends JpaRepository<ApplicationVariable, String> {
    List<ApplicationVariable> findAllByTenantIdAndApplicationIdOrderByIdAsc(String tenantId, String applicationId);
}