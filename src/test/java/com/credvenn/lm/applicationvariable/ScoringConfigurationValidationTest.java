package com.credvenn.lm.applicationvariable;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.validation.Validation;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScoringConfigurationValidationTest {
    private final jakarta.validation.Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private ApplicationVariableDtos.DefinitionRequest request(BigDecimal weight, BigDecimal points) {
        return new ApplicationVariableDtos.DefinitionRequest("INCOME", "Income", null,
                ApplicationVariableFieldType.SINGLE_SELECT, true, 1, null, null,
                List.of(new ApplicationVariableDtos.Option("SALARY", "Salary", points)), null, weight);
    }

    @Test void weightAndPointsAreOptionalAndValidValuesAreAccepted() {
        assertTrue(validator.validate(request(null, null)).isEmpty());
        assertTrue(validator.validate(request(new BigDecimal("10.00"), new BigDecimal("100"))).isEmpty());
    }

    @Test void rejectsNegativeWeightAndOutOfRangeAnswerPoints() {
        assertFalse(validator.validate(request(new BigDecimal("-1"), BigDecimal.TEN)).isEmpty());
        assertFalse(validator.validate(request(BigDecimal.ONE, new BigDecimal("100.01"))).isEmpty());
        assertFalse(validator.validate(request(BigDecimal.ONE, new BigDecimal("-0.01"))).isEmpty());
    }
}
