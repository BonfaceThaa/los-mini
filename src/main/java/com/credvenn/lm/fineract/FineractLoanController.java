package com.credvenn.lm.fineract;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/loans")
@Tag(name = "Fineract Loans")
@SecurityRequirement(name = "bearerAuth")
public class FineractLoanController {

    private final FineractLoanService fineractLoanService;

    public FineractLoanController(FineractLoanService fineractLoanService) {
        this.fineractLoanService = fineractLoanService;
    }

    @GetMapping("/dashboard/overdue")
    @PreAuthorize("hasAuthority('LOAN_VIEW')")
    @Operation(
            summary = "Fetch overdue loans for the authenticated tenant dashboard",
            description = "Calls the Fineract overdue loans run report and returns a frontend-friendly dashboard payload with filters, summary totals, and named items.")
    public ResponseEntity<FineractDtos.OverdueLoanDashboardResponse> getOverdueLoansDashboard(
            @Parameter(description = "Fineract office id. Defaults to the configured backend office when omitted.")
            @RequestParam(required = false) Integer officeId,
            @Parameter(description = "Fineract loan officer id. Use -1 for all loan officers.")
            @RequestParam(defaultValue = "-1") Integer loanOfficerId,
            @Parameter(description = "Lower amount bound for the report filter. Use 0 for no lower amount bound.")
            @RequestParam(defaultValue = "0") Integer fromAmount,
            @Parameter(description = "Upper amount bound for the report filter. Use 0 for no upper amount bound.")
            @RequestParam(defaultValue = "0") Integer toAmount,
            @Parameter(description = "Minimum overdue days to include in the report.")
            @RequestParam(defaultValue = "1") Integer overdueFromDays,
            @Parameter(description = "Maximum overdue days to include in the report.")
            @RequestParam(defaultValue = "30") Integer overdueToDays) {
        return ResponseEntity.ok(fineractLoanService.getCurrentTenantOverdueLoanDashboard(
                officeId,
                loanOfficerId,
                fromAmount,
                toAmount,
                overdueFromDays,
                overdueToDays));
    }

    @GetMapping("/{loanId}/repayment-schedule")
    @PreAuthorize("hasAuthority('LOAN_VIEW')")
    @Operation(summary = "Fetch a loan repayment schedule from Fineract for the authenticated tenant")
    public ResponseEntity<FineractDtos.RepaymentScheduleResponse> getRepaymentSchedule(@PathVariable String loanId) {
        return ResponseEntity.ok(fineractLoanService.getCurrentTenantRepaymentSchedule(loanId));
    }
}
