package com.credvenn.lm.subscription;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, String> {

    boolean existsByPlanCodeIgnoreCase(String planCode);

    Optional<SubscriptionPlan> findByPlanCodeIgnoreCase(String planCode);

    List<SubscriptionPlan> findAllByOrderByNameAsc();
}
