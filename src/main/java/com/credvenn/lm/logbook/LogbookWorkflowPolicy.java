package com.credvenn.lm.logbook;

import com.credvenn.lm.application.LoanRequestApplication;
import com.credvenn.lm.common.exception.*;
import com.credvenn.lm.origination.*;
import com.credvenn.lm.origination.OriginationProfileDtos.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor
public class LogbookWorkflowPolicy {
    private final OriginationProfileRepository profiles;
    private final OriginationProfileValidator validator;
    private final ObjectMapper json;
    public boolean applies(LoanRequestApplication application) {
        if (application.getOriginationProfileId() == null) return false;
        return isLogbook(requirements(profile(application)));
    }
    public static boolean isLogbook(Map<Stage,List<Requirement>> requirements) {
        return requirements.values().stream().filter(Objects::nonNull).flatMap(List::stream).anyMatch(r -> r == Requirement.VALUATION_APPROVED);
    }
    public Map<Stage,List<Requirement>> required(LoanRequestApplication application) {
        var profile = profile(application);
        var requirements = requirements(profile);
        try {
            validator.validate(requirements, json.readValue(profile.getConfigurationJson(), Configuration.class), true);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException("Invalid stored profile configuration",e); }
        if (!isLogbook(requirements)) throw new BadRequestException("Application does not use a logbook workflow");
        return requirements;
    }
    private OriginationProfile profile(LoanRequestApplication application) {
        return profiles.findByTenantIdAndId(application.getTenantId(), application.getOriginationProfileId())
            .orElseThrow(() -> new NotFoundException("Application profile not found"));
    }
    private Map<Stage,List<Requirement>> requirements(OriginationProfile profile) {
        try { return json.readValue(profile.getRequirementsJson(),new TypeReference<>(){}); }
        catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException("Invalid stored profile requirements",e);}
    }
}
