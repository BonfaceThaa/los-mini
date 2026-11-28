package com.credvenn.lm.scoring;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Calculates from a caller-supplied immutable scorecard and answer snapshot. */
@Component
public class QuestionnaireScoreCalculator {
    public record Option(String code, BigDecimal points) {}
    public record Condition(String questionCode, Set<String> applicableValues) {}
    public record Question(String code, int definitionVersion, boolean required, BigDecimal weight,
                           boolean multiSelect, List<Option> options, Condition condition) {}
    public record Answer(String questionCode, int definitionVersion, List<String> selectedValues) {}
    public record Factor(String questionCode, int definitionVersion, String outcome, BigDecimal weight,
                         List<String> selectedValues, BigDecimal responseScore, BigDecimal weightedPoints,
                         String reasonCode) {}
    public record Result(String status, BigDecimal score, List<Factor> factors, List<String> reasonCodes) {}

    /**
     * The scorecard snapshot, rather than the points on submitted answers, is authoritative.
     * A missing or incompatible source answer cannot be interpreted as a favourable answer.
     */
    public Result calculate(List<Question> questions, List<Answer> answers) {
        if (questions == null || answers == null) throw new IllegalArgumentException("Snapshots are required");
        Map<String, Question> byCode = new HashMap<>();
        for (Question q : questions) {
            if (q == null || q.code() == null || q.code().isBlank() || q.definitionVersion() < 1
                    || q.weight() == null || q.weight().signum() < 0 || q.options() == null || q.options().isEmpty()
                    || byCode.putIfAbsent(q.code(), q) != null) throw new IllegalArgumentException("Invalid question snapshot");
            Set<String> options = new HashSet<>();
            for (Option o : q.options()) {
                if (o == null || o.code() == null || o.code().isBlank() || !options.add(o.code())
                        || o.points() == null || o.points().signum() < 0 || o.points().compareTo(BigDecimal.valueOf(100)) > 0)
                    throw new IllegalArgumentException("Invalid option snapshot: " + q.code());
            }
        }
        Map<String, Answer> answerMap = new HashMap<>();
        for (Answer answer : answers) {
            if (answer == null || answer.questionCode() == null || answerMap.putIfAbsent(answer.questionCode(), answer) != null)
                throw new IllegalArgumentException("Invalid or duplicate answer");
        }
        List<Factor> factors = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        BigDecimal numerator = BigDecimal.ZERO;
        BigDecimal denominator = BigDecimal.ZERO;
        for (Question q : questions) {
            if (q.condition() != null) {
                Condition c = q.condition();
                Question parent = byCode.get(c.questionCode());
                if (parent == null || parent.condition() != null || c.applicableValues() == null || c.applicableValues().isEmpty())
                    throw new IllegalArgumentException("Invalid applicability condition: " + q.code());
                Set<String> parentOptions = new HashSet<>();
                parent.options().forEach(o -> parentOptions.add(o.code()));
                if (!parentOptions.containsAll(c.applicableValues())) throw new IllegalArgumentException("Unknown condition option: " + q.code());
                Answer trigger = answerMap.get(parent.code());
                String issue = answerIssue(parent, trigger);
                if (issue != null) {
                    String reason = "APPLICABILITY_UNDETERMINED:" + q.code();
                    reasons.add(reason);
                    factors.add(new Factor(q.code(), q.definitionVersion(), "UNKNOWN", q.weight(), List.of(), null, null, reason));
                    continue;
                }
                if (trigger.selectedValues().stream().noneMatch(c.applicableValues()::contains)) {
                    factors.add(new Factor(q.code(), q.definitionVersion(), "NOT_APPLICABLE", q.weight(), List.of(), null, null, null));
                    continue;
                }
            }
            Answer a = answerMap.get(q.code());
            String issue = answerIssue(q, a);
            if ("MISSING".equals(issue) && !q.required()) {
                factors.add(new Factor(q.code(), q.definitionVersion(), "OPTIONAL_MISSING", q.weight(), List.of(), null, null, null));
                continue;
            }
            if (issue != null) {
                String reason = ("MISSING".equals(issue) ? "REQUIRED_ANSWER_MISSING" : issue) + ":" + q.code();
                reasons.add(reason);
                factors.add(new Factor(q.code(), q.definitionVersion(), "UNKNOWN", q.weight(), List.of(), null, null, reason));
                continue;
            }
            Map<String, BigDecimal> scores = new HashMap<>();
            q.options().forEach(o -> scores.put(o.code(), o.points()));
            BigDecimal points = a.selectedValues().stream().map(scores::get).min(BigDecimal::compareTo).orElseThrow();
            BigDecimal weighted = q.weight().multiply(points);
            numerator = numerator.add(weighted);
            denominator = denominator.add(q.weight());
            factors.add(new Factor(q.code(), q.definitionVersion(), "SCORED", q.weight(), List.copyOf(a.selectedValues()), points, weighted, null));
        }
        if (denominator.signum() == 0) reasons.add("NO_APPLICABLE_WEIGHT");
        BigDecimal score = reasons.isEmpty() ? numerator.divide(denominator, 2, RoundingMode.HALF_UP) : null;
        return new Result(reasons.isEmpty() ? "COMPLETE" : "REVIEW", score, List.copyOf(factors), List.copyOf(reasons));
    }

    private String answerIssue(Question q, Answer a) {
        if (a == null || a.selectedValues() == null || a.selectedValues().isEmpty()) return "MISSING";
        if (a.definitionVersion() != q.definitionVersion()) return "ANSWER_VERSION_MISMATCH";
        if ((!q.multiSelect() && a.selectedValues().size() != 1)
                || new HashSet<>(a.selectedValues()).size() != a.selectedValues().size()) return "INVALID_ANSWER";
        Set<String> codes = new HashSet<>();
        q.options().forEach(o -> codes.add(o.code()));
        return a.selectedValues().stream().allMatch(codes::contains) ? null : "INVALID_ANSWER";
    }
}
