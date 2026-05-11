package com.credvenn.lm.kyc;

import com.credvenn.lm.application.LoanRequestApplication;

public interface KycProvider {

    String providerCode();

    KycDecision assess(LoanRequestApplication application);

    record KycDecision(KycStatus status, String providerReference, String summary) {
    }
}
