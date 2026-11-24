package com.credvenn.stmt.analyzer.core.analysis;

import com.credvenn.stmt.analyzer.core.AnalysisResult.Diagnostic;
import java.math.BigDecimal;
import java.util.List;

public record ReconciliationResult(boolean reconciled, BigDecimal totalCredits, BigDecimal totalDebits,
        BigDecimal parsingConfidence, List<Diagnostic> errors, List<Diagnostic> warnings) {
    public boolean tolerated() { return warnings.stream().anyMatch(w -> w.code().equals("RECONCILIATION_TOLERANCE_APPLIED")); }
    public ReconciliationResult { errors = List.copyOf(errors); warnings = List.copyOf(warnings); }
}
