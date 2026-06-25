package com.credvenn.lm.kyc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class KycServiceTest {

    @Test
    void toResponseIncludesStructuredActionDetails() {
        KycCheck check = new KycCheck();
        check.setApplicationId("app-1");
        check.setProvider("SMILE_ID");
        check.setStatus(KycStatus.MANUAL_REVIEW_REQUIRED);
        check.setProviderReference("1000000002");
        check.setSummary("Smile ID summary");
        check.setActionNames("Partial Match");
        check.setActionFirstName("Exact Match");
        check.setActionLastName("No Match");
        check.setActionOtherNames(null);
        check.setActionDob("Not Provided");
        check.setActionGender("Not Provided");
        check.setActionPhoneNumber("Not Provided");
        check.setActionVerifyIdNumber("Verified");
        check.setActionIdVerification("Partial Match");

        KycDtos.KycCheckResponse response = KycService.toResponse(check);

        assertEquals("SMILE_ID", response.provider());
        assertEquals("1000000002", response.providerReference());
        assertEquals("Partial Match", response.actions().names());
        assertEquals("Exact Match", response.actions().firstName());
        assertEquals("No Match", response.actions().lastName());
        assertNull(response.actions().otherNames());
        assertEquals("Not Provided", response.actions().dob());
        assertEquals("Not Provided", response.actions().gender());
        assertEquals("Not Provided", response.actions().phoneNumber());
        assertEquals("Verified", response.actions().verifyIdNumber());
        assertEquals("Partial Match", response.actions().idVerification());
    }

    @Test
    void toResponseLeavesActionsNullWhenNoStructuredDetailsExist() {
        KycCheck check = new KycCheck();
        check.setApplicationId("app-1");
        check.setProvider("MANUAL");
        check.setStatus(KycStatus.MANUALLY_APPROVED);

        KycDtos.KycCheckResponse response = KycService.toResponse(check);

        assertNull(response.actions());
    }
}
