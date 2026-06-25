package com.credvenn.lm.kyc;

import com.credvenn.lm.application.LoanRequestApplication;
import org.springframework.stereotype.Component;

@Component
public class SimulatedKycProvider implements KycProvider {

    @Override
    public String providerCode() {
        return "SIMULATED";
    }

    @Override
    public KycDecision assess(LoanRequestApplication application) {
        String nationalId = application.getNationalId().trim();
        char last = nationalId.charAt(nationalId.length() - 1);
        if (last == '0' || last == '5') {
            return new KycDecision(KycStatus.FAILED, "SIM-KYC-" + application.getId(), "Deterministic simulated failure", null);
        }
        if (last == '1' || last == '6') {
            return new KycDecision(KycStatus.MANUAL_REVIEW_REQUIRED, "SIM-KYC-" + application.getId(), "Deterministic simulated manual review", null);
        }
        return new KycDecision(KycStatus.PASSED, "SIM-KYC-" + application.getId(), "Deterministic simulated pass", null);
    }
}
