package com.credvenn.lm.scoring;

import static org.junit.jupiter.api.Assertions.*;
import static com.credvenn.lm.scoring.QuestionnaireScoreCalculator.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class QuestionnaireScoreCalculatorTest {
    private final QuestionnaireScoreCalculator calculator = new QuestionnaireScoreCalculator();
    private BigDecimal n(String value) { return new BigDecimal(value); }
    private Option option(String code, int score) { return new Option(code, BigDecimal.valueOf(score)); }
    private Question question(String code, int weight, boolean required, boolean multi, Condition condition, Option... options) {
        return new Question(code, 1, required, BigDecimal.valueOf(weight), multi, List.of(options), condition);
    }
    private Answer answer(String code, String... values) { return new Answer(code, 1, List.of(values)); }

    @Test void normalizesApplicableQuestionsAndUsesLowestSelectedPoints() {
        var questions = List.of(
                question("LOANS", 10, true, false, null, option("YES", 50), option("NO", 100)),
                question("TYPES", 10, true, true, new Condition("LOANS", Set.of("YES")), option("BANK", 80), option("FULIZA", 40)),
                question("DURATION", 5, false, false, null, option("LONG", 100)));
        var result = calculator.calculate(questions, List.of(answer("LOANS", "YES"), answer("TYPES", "BANK", "FULIZA")));
        assertEquals("COMPLETE", result.status());
        assertEquals(n("45.00"), result.score());
        assertEquals(n("40"), result.factors().get(1).responseScore());
        assertEquals("OPTIONAL_MISSING", result.factors().get(2).outcome());
        var noLoan = calculator.calculate(questions, List.of(answer("LOANS", "NO")));
        assertEquals(n("100.00"), noLoan.score());
        assertEquals("NOT_APPLICABLE", noLoan.factors().get(1).outcome());
    }

    @Test void missingInvalidOrIncompatibleAnswersRequireReview() {
        var questions = List.of(question("INCOME", 10, true, false, null, option("BUSINESS", 80), option("SALARY", 100)),
                question("BUSINESS_AGE", 5, true, false, new Condition("INCOME", Set.of("BUSINESS")), option("LONG", 100)));
        assertEquals("REVIEW", calculator.calculate(questions, List.of()).status());
        var invalid = calculator.calculate(questions, List.of(answer("INCOME", "BUSINESS"), answer("BUSINESS_AGE", "UNKNOWN")));
        assertNull(invalid.score());
        assertTrue(invalid.reasonCodes().contains("INVALID_ANSWER:BUSINESS_AGE"));
        var mismatch = calculator.calculate(questions, List.of(new Answer("INCOME", 2, List.of("SALARY"))));
        assertTrue(mismatch.reasonCodes().contains("ANSWER_VERSION_MISMATCH:INCOME"));
        var salary = calculator.calculate(questions, List.of(answer("INCOME", "SALARY")));
        assertEquals(n("100.00"), salary.score());
    }

    @Test void zeroWeightAndInvalidConfigurationsCannotProduceScore() {
        var zero = question("A", 0, true, false, null, option("X", 100));
        assertEquals("REVIEW", calculator.calculate(List.of(zero), List.of(answer("A", "X"))).status());
        var bad = question("B", 1, true, false, new Condition("MISSING", Set.of("X")), option("Y", 100));
        assertThrows(IllegalArgumentException.class, () -> calculator.calculate(List.of(bad), List.of()));
    }
}
