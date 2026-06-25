package com.credvenn.lm.kyc;

import com.credvenn.lm.application.LoanRequestApplication;

public interface SmileIdGateway {

    SmileIdDtos.VerifyResponse verifyBasicKyc(LoanRequestApplication application);
}
