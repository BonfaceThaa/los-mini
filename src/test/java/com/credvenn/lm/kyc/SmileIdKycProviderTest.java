package com.credvenn.lm.kyc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.credvenn.lm.application.ApplicantIdType;
import com.credvenn.lm.application.LoanRequestApplication;
import org.junit.jupiter.api.Test;

class SmileIdKycProviderTest {

    @Test
    void assessMapsAndNormalizesSmileIdActionDetails() {
        SmileIdGateway gateway = application -> new SmileIdDtos.VerifyResponse(
                "1000000002",
                "Partial Match",
                "1021",
                new SmileIdDtos.Actions(
                        "Verified",
                        "Partial Match",
                        "Exact Match",
                        "No Match",
                        "   ",
                        "Not Provided",
                        "Not Provided",
                        "Not Provided",
                        "Partial Match",
                        "Not Applicable"),
                "signature",
                "2026-06-19T05:17:08.684Z");

        SmileIdKycProvider provider = new SmileIdKycProvider(gateway);

        KycProvider.KycDecision decision = provider.assess(application());

        assertEquals(KycStatus.MANUAL_REVIEW_REQUIRED, decision.status());
        assertEquals("1000000002", decision.providerReference());
        assertEquals("Partial Match", decision.actionDetails().names());
        assertEquals("Exact Match", decision.actionDetails().firstName());
        assertEquals("No Match", decision.actionDetails().lastName());
        assertNull(decision.actionDetails().otherNames());
        assertEquals("Not Provided", decision.actionDetails().dob());
        assertEquals("Not Provided", decision.actionDetails().gender());
        assertEquals("Not Provided", decision.actionDetails().phoneNumber());
        assertEquals("Verified", decision.actionDetails().verifyIdNumber());
        assertEquals("Partial Match", decision.actionDetails().idVerification());
    }

    private LoanRequestApplication application() {
        LoanRequestApplication application = new LoanRequestApplication();
        application.setTenantId("tenant-1");
        application.setApplicantFirstName("Jane");
        application.setApplicantLastName("Doe");
        application.setPhoneNumber("254700000000");
        application.setNationalId("32162157");
        application.setApplicantIdType(ApplicantIdType.NATIONAL_ID);
        return application;
    }
}
