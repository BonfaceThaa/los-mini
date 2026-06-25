package com.credvenn.lm.kyc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class SmileIdDtos {

    private SmileIdDtos() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record VerifyRequest(
            String source_sdk,
            String source_sdk_version,
            String partner_id,
            String signature,
            String timestamp,
            String country,
            String id_type,
            String id_number,
            String first_name,
            String middle_name,
            String last_name,
            String dob,
            String gender,
            String phone_number,
            Integer job_type,
            PartnerParams partner_params) {
    }

    public record PartnerParams(
            String job_id,
            String user_id) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VerifyResponse(
            @JsonProperty("SmileJobID") String smileJobId,
            @JsonProperty("ResultText") String resultText,
            @JsonProperty("ResultCode") String resultCode,
            @JsonProperty("Actions") Actions actions,
            @JsonProperty("signature") String signature,
            @JsonProperty("timestamp") String timestamp) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Actions(
            @JsonProperty("Verify_ID_Number") String verifyIdNumber,
            @JsonProperty("Names") String names,
            @JsonProperty("FirstName") String firstName,
            @JsonProperty("LastName") String lastName,
            @JsonProperty("OtherNames") String otherNames,
            @JsonProperty("DOB") String dob,
            @JsonProperty("Gender") String gender,
            @JsonProperty("Phone_Number") String phoneNumber,
            @JsonProperty("ID_Verification") String idVerification,
            @JsonProperty("Return_Personal_Info") String returnPersonalInfo) {
    }
}
