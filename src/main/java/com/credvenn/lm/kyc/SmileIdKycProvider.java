package com.credvenn.lm.kyc;

import com.credvenn.lm.application.LoanRequestApplication;
import com.credvenn.lm.common.exception.BadRequestException;
import org.springframework.stereotype.Component;

@Component
public class SmileIdKycProvider implements KycProvider {

    private final SmileIdGateway smileIdGateway;

    public SmileIdKycProvider(SmileIdGateway smileIdGateway) {
        this.smileIdGateway = smileIdGateway;
    }

    @Override
    public String providerCode() {
        return "SMILE_ID";
    }

    @Override
    public KycDecision assess(LoanRequestApplication application) {
        SmileIdDtos.VerifyResponse response = smileIdGateway.verifyBasicKyc(application);
        String resultCode = response.resultCode();
        String summary = summarize(response);
        KycActionDetails actionDetails = toActionDetails(response.actions());
        return switch (resultCode == null ? "" : resultCode.trim()) {
            case "1020" -> new KycDecision(KycStatus.PASSED, response.smileJobId(), summary, actionDetails);
            case "1021", "1015" -> new KycDecision(KycStatus.MANUAL_REVIEW_REQUIRED, response.smileJobId(), summary, actionDetails);
            case "1022", "1013", "1014", "2413", "2204", "2213", "0001" ->
                    new KycDecision(KycStatus.FAILED, response.smileJobId(), summary, actionDetails);
            case "2405", "2205", "2212", "2220" ->
                    throw new BadRequestException("Smile ID request rejected: " + summary);
            default -> new KycDecision(KycStatus.MANUAL_REVIEW_REQUIRED, response.smileJobId(), summary, actionDetails);
        };
    }

    private String summarize(SmileIdDtos.VerifyResponse response) {
        SmileIdDtos.Actions actions = response.actions();
        String summary = String.format(
                "Smile ID resultCode=%s resultText=%s verifyIdNumber=%s names=%s firstName=%s lastName=%s otherNames=%s dob=%s gender=%s phone=%s idVerification=%s",
                response.resultCode(),
                response.resultText(),
                actions == null ? null : actions.verifyIdNumber(),
                actions == null ? null : actions.names(),
                actions == null ? null : actions.firstName(),
                actions == null ? null : actions.lastName(),
                actions == null ? null : actions.otherNames(),
                actions == null ? null : actions.dob(),
                actions == null ? null : actions.gender(),
                actions == null ? null : actions.phoneNumber(),
                actions == null ? null : actions.idVerification());
        return summary.length() > 1000 ? summary.substring(0, 1000) : summary;
    }

    private KycActionDetails toActionDetails(SmileIdDtos.Actions actions) {
        if (actions == null) {
            return null;
        }
        KycActionDetails details = new KycActionDetails(
                normalize(actions.names()),
                normalize(actions.firstName()),
                normalize(actions.lastName()),
                normalize(actions.otherNames()),
                normalize(actions.dob()),
                normalize(actions.gender()),
                normalize(actions.phoneNumber()),
                normalize(actions.verifyIdNumber()),
                normalize(actions.idVerification()));
        return isEmpty(details) ? null : details;
    }

    private boolean isEmpty(KycActionDetails details) {
        return details.names() == null
                && details.firstName() == null
                && details.lastName() == null
                && details.otherNames() == null
                && details.dob() == null
                && details.gender() == null
                && details.phoneNumber() == null
                && details.verifyIdNumber() == null
                && details.idVerification() == null;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
