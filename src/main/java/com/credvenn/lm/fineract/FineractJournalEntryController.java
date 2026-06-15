package com.credvenn.lm.fineract;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/journal-entries")
@Tag(name = "Fineract Journal Entries")
@SecurityRequirement(name = "bearerAuth")
public class FineractJournalEntryController {

    private final FineractLoanService fineractLoanService;

    public FineractJournalEntryController(FineractLoanService fineractLoanService) {
        this.fineractLoanService = fineractLoanService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('LOAN_VIEW')")
    @Operation(summary = "Fetch journal entries from Fineract for the authenticated tenant")
    public ResponseEntity<FineractDtos.JournalEntryListResponse> getJournalEntries(
            @RequestParam(required = false) Integer offset,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String orderBy,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) Integer officeId,
            @RequestParam(required = false) Long glAccountId,
            @RequestParam(required = false) Boolean manualEntriesOnly,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String transactionId,
            @RequestParam(required = false) Boolean transactionDetails,
            @RequestParam(required = false) Boolean runningBalance,
            @RequestParam(required = false) Long loanId) {
        return ResponseEntity.ok(fineractLoanService.getCurrentTenantJournalEntries(new FineractGateway.JournalEntryQuery(
                offset,
                limit,
                orderBy,
                sortBy,
                officeId,
                glAccountId,
                manualEntriesOnly,
                fromDate,
                toDate,
                transactionId,
                transactionDetails,
                runningBalance,
                loanId)));
    }
}
