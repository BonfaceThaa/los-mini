package com.credvenn.lm.fineract;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
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

    @GetMapping("/{loanId}/repayment-schedule")
    @PreAuthorize("hasAuthority('LOAN_VIEW')")
    @Operation(summary = "Fetch a loan repayment schedule from Fineract for the authenticated tenant")
    public ResponseEntity<FineractDtos.RepaymentScheduleResponse> getRepaymentSchedule(@PathVariable String loanId) {
        return ResponseEntity.ok(fineractLoanService.getCurrentTenantRepaymentSchedule(loanId));
    }
}
