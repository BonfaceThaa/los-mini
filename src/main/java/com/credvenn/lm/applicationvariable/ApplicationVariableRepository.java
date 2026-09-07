package com.credvenn.lm.applicationvariable;
import java.util.*;import org.springframework.data.jpa.repository.JpaRepository;
public interface ApplicationVariableRepository extends JpaRepository<ApplicationVariable,String>{List<ApplicationVariable> findAllByTenantIdAndApplicationId(String tenantId,String applicationId);}
