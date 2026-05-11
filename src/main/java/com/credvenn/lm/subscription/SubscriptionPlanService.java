package com.credvenn.lm.subscription;

import com.credvenn.lm.common.exception.ConflictException;
import com.credvenn.lm.common.exception.NotFoundException;
import com.credvenn.lm.security.CurrentActorService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionPlanService {

    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final SubscriptionPolicy subscriptionPolicy;
    private final CurrentActorService currentActorService;

    public SubscriptionPlanService(
            SubscriptionPlanRepository subscriptionPlanRepository,
            SubscriptionPolicy subscriptionPolicy,
            CurrentActorService currentActorService) {
        this.subscriptionPlanRepository = subscriptionPlanRepository;
        this.subscriptionPolicy = subscriptionPolicy;
        this.currentActorService = currentActorService;
    }

    @Transactional
    public SubscriptionDtos.SubscriptionPlanResponse create(SubscriptionDtos.CreateSubscriptionPlanRequest request) {
        subscriptionPolicy.validatePlan(request);
        if (subscriptionPlanRepository.existsByPlanCodeIgnoreCase(request.planCode().trim())) {
            throw new ConflictException("Subscription plan code already exists");
        }
        String actor = currentActorService.requireCurrentUser().username();
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setPlanCode(request.planCode().trim().toUpperCase());
        plan.setName(request.name().trim());
        plan.setDescription(request.description());
        plan.setMaxUsers(request.maxUsers());
        plan.setMaxBranches(request.maxBranches());
        plan.setMonthlyApplicationLimit(request.monthlyApplicationLimit());
        plan.setApprovedApplicationThreshold(request.approvedApplicationThreshold());
        plan.setMonthlyFee(request.monthlyFee());
        plan.setInterestSharePercentage(request.interestSharePercentage());
        plan.setCurrency(request.currency().trim().toUpperCase());
        plan.setActive(true);
        plan.setCreatedBy(actor);
        plan.setUpdatedBy(actor);
        return toResponse(subscriptionPlanRepository.save(plan));
    }

    @Transactional
    public SubscriptionDtos.SubscriptionPlanResponse update(String planId, SubscriptionDtos.UpdateSubscriptionPlanRequest request) {
        subscriptionPolicy.validatePlan(request);
        SubscriptionPlan plan = getRequiredEntity(planId);
        String actor = currentActorService.requireCurrentUser().username();
        plan.setName(request.name().trim());
        plan.setDescription(request.description());
        plan.setMaxUsers(request.maxUsers());
        plan.setMaxBranches(request.maxBranches());
        plan.setMonthlyApplicationLimit(request.monthlyApplicationLimit());
        plan.setApprovedApplicationThreshold(request.approvedApplicationThreshold());
        plan.setMonthlyFee(request.monthlyFee());
        plan.setInterestSharePercentage(request.interestSharePercentage());
        plan.setCurrency(request.currency().trim().toUpperCase());
        plan.setActive(request.active());
        plan.setUpdatedBy(actor);
        return toResponse(plan);
    }

    @Transactional(readOnly = true)
    public SubscriptionPlan getRequiredEntity(String planId) {
        return subscriptionPlanRepository.findById(planId)
                .orElseThrow(() -> new NotFoundException("Subscription plan not found"));
    }

    @Transactional(readOnly = true)
    public SubscriptionDtos.SubscriptionPlanResponse getById(String planId) {
        return toResponse(getRequiredEntity(planId));
    }

    @Transactional(readOnly = true)
    public List<SubscriptionDtos.SubscriptionPlanResponse> list() {
        return subscriptionPlanRepository.findAllByOrderByNameAsc().stream().map(SubscriptionPlanService::toResponse).toList();
    }

    static SubscriptionDtos.SubscriptionPlanResponse toResponse(SubscriptionPlan plan) {
        return new SubscriptionDtos.SubscriptionPlanResponse(
                plan.getId(),
                plan.getPlanCode(),
                plan.getName(),
                plan.getDescription(),
                plan.getMaxUsers(),
                plan.getMaxBranches(),
                plan.getMonthlyApplicationLimit(),
                plan.getApprovedApplicationThreshold(),
                plan.getMonthlyFee(),
                plan.getInterestSharePercentage(),
                plan.getCurrency(),
                plan.isActive(),
                plan.getCreatedBy(),
                plan.getUpdatedBy(),
                plan.getCreatedAt(),
                plan.getUpdatedAt());
    }
}
