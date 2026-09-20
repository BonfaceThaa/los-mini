package com.credvenn.lm.fineract;

import com.credvenn.lm.common.api.PagedResponse;
import com.credvenn.lm.loanproduct.LoanProductCatalogDtos;
import com.credvenn.lm.loanproduct.LoanProductCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/loan-products")
@Tag(name = "Fineract Loan Products")
@SecurityRequirement(name = "bearerAuth")
public class FineractLoanProductController {

    private final LoanProductCatalogService loanProductCatalogService;

    public FineractLoanProductController(LoanProductCatalogService loanProductCatalogService) {
        this.loanProductCatalogService = loanProductCatalogService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('LOAN_VIEW')")
    @Operation(summary = "List tenant-owned loan products for the authenticated tenant", description = "Requires LOAN_VIEW. Omit originationProfileCode for the tenant-wide catalog; supply it to filter by profile, including draft profiles. includeInactive controls product activity, not profile activity.")
    public ResponseEntity<PagedResponse<FineractDtos.LoanProductResponse>> listLoanProducts(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            @RequestParam(defaultValue = "false") boolean includeInactive,
            @Parameter(description = "Optional tenant profile filter. Omitted means all profiles; it does not use the tenant default.", example = "LOGBOOK")
            @RequestParam(required = false) String originationProfileCode) {
        return ResponseEntity.ok(loanProductCatalogService.listCurrentTenantLoanProducts(page, size, sortBy, sortDir, includeInactive, originationProfileCode));
    }

    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Created product mapping with resolved profile ID", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = LoanProductCatalogDtos.LoanProductCatalogResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid product configuration, missing active tenant default or missing accounting setup", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.credvenn.lm.common.exception.ApiError.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Missing LOAN_CREATE permission or tenant context", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.credvenn.lm.common.exception.ApiError.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Origination profile not found in the authenticated tenant", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.credvenn.lm.common.exception.ApiError.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Duplicate product code or unavailable tenant default profile", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = com.credvenn.lm.common.exception.ApiError.class)))
    })
    @PostMapping
    @PreAuthorize("hasAuthority('LOAN_CREATE')")
    @Operation(summary = "Create a tenant loan product in Mini-LOS and Fineract",
            description = "Requires LOAN_CREATE. Omitted/null profile code uses the active tenant default; explicit codes may identify draft profiles. Profile validation precedes Fineract creation. Omit accountingAccounts to use configured tenant GL accounts, or supply all nine IDs. A logbook product can be configured now, but logbook application workflow execution is not enabled yet.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                content = @io.swagger.v3.oas.annotations.media.Content(
                    schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = LoanProductCatalogDtos.CreateLoanProductRequest.class),
                    examples = @io.swagger.v3.oas.annotations.media.ExampleObject(name = "Logbook product", value = LoanProductCatalogDtos.LOGBOOK_EXAMPLE))))
    public ResponseEntity<LoanProductCatalogDtos.LoanProductCatalogResponse> createLoanProduct(
            @Valid @RequestBody LoanProductCatalogDtos.CreateLoanProductRequest request) {
        return ResponseEntity.ok(loanProductCatalogService.createCurrentTenantProduct(request));
    }

    @GetMapping("/{productCode}")
    @PreAuthorize("hasAuthority('LOAN_VIEW')")
    @Operation(summary = "Get a tenant loan product mapping by product code")
    public ResponseEntity<LoanProductCatalogDtos.LoanProductCatalogResponse> getLoanProduct(@PathVariable String productCode) {
        return ResponseEntity.ok(loanProductCatalogService.getCurrentTenantProduct(productCode));
    }

    @PatchMapping("/{shortName}")
    @PreAuthorize("hasAuthority('LOAN_PRODUCT_UPDATE')")
    @Operation(summary = "Update a tenant loan product in Mini-LOS and Fineract by short name", description = "Requires LOAN_PRODUCT_UPDATE. Omitted fields retain their values. Preserves the origination association; use PUT /loan-products/{productCode}/origination-profile to change that association.")
    public ResponseEntity<LoanProductCatalogDtos.LoanProductCatalogResponse> updateLoanProduct(
            @PathVariable String shortName,
            @Valid @RequestBody LoanProductCatalogDtos.UpdateLoanProductRequest request) {
        return ResponseEntity.ok(loanProductCatalogService.updateCurrentTenantProductByShortName(shortName, request));
    }

    @PostMapping("/{shortName}/deactivate")
    @PreAuthorize("hasAuthority('LOAN_PRODUCT_UPDATE')")
    @Operation(summary = "Deactivate a tenant loan product in Mini-LOS and Fineract by short name")
    public ResponseEntity<LoanProductCatalogDtos.LoanProductCatalogResponse> deactivateLoanProduct(@PathVariable String shortName) {
        return ResponseEntity.ok(loanProductCatalogService.deactivateCurrentTenantProductByShortName(shortName));
    }

    @DeleteMapping("/{shortName}")
    @PreAuthorize("hasAuthority('LOAN_PRODUCT_UPDATE')")
    @Operation(summary = "Delete an inactive tenant loan product mapping by short name")
    public ResponseEntity<Void> deleteLoanProduct(@PathVariable String shortName) {
        loanProductCatalogService.deleteCurrentTenantProductByShortName(shortName);
        return ResponseEntity.noContent().build();
    }
}


