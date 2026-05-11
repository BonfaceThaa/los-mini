package com.credvenn.lm.subscription;

import com.credvenn.lm.common.exception.BadRequestException;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class SubscriptionPolicy {

    public void validatePlan(SubscriptionDtos.CreateSubscriptionPlanRequest request) {
        validatePlanValues(
                request.maxUsers(),
                request.maxBranches(),
                request.monthlyApplicationLimit(),
                request.approvedApplicationThreshold(),
                request.monthlyFee(),
                request.interestSharePercentage());
    }

    public void validatePlan(SubscriptionDtos.UpdateSubscriptionPlanRequest request) {
        validatePlanValues(
                request.maxUsers(),
                request.maxBranches(),
                request.monthlyApplicationLimit(),
                request.approvedApplicationThreshold(),
                request.monthlyFee(),
                request.interestSharePercentage());
    }

    public void validateBillingPeriod(Instant currentPeriodStart, Instant currentPeriodEnd) {
        if (currentPeriodStart == null || currentPeriodEnd == null || !currentPeriodEnd.isAfter(currentPeriodStart)) {
            throw new BadRequestException("Subscription billing period is invalid");
        }
    }

    private void validatePlanValues(
            Integer maxUsers,
            Integer maxBranches,
            Integer monthlyApplicationLimit,
            Integer approvedApplicationThreshold,
            BigDecimal monthlyFee,
            BigDecimal interestSharePercentage) {
        if (maxUsers == null || maxUsers < 0
                || maxBranches == null || maxBranches < 0
                || monthlyApplicationLimit == null || monthlyApplicationLimit < 0
                || approvedApplicationThreshold == null || approvedApplicationThreshold < 0) {
            throw new BadRequestException("Plan limits must be zero or greater");
        }
        if (monthlyFee == null || monthlyFee.signum() < 0) {
            throw new BadRequestException("Monthly fee must be zero or greater");
        }
        if (interestSharePercentage == null || interestSharePercentage.signum() < 0) {
            throw new BadRequestException("Interest share percentage must be zero or greater");
        }
    }
}
