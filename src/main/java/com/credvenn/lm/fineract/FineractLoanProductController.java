package com.credvenn.lm.fineract;

import com.credvenn.lm.common.api.PagedResponse;
import com.credvenn.lm.loanproduct.LoanProductCatalogDtos;
import com.credvenn.lm.loanproduct.LoanProductCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    @Operation(summary = "List active tenant-owned loan products for the authenticated tenant")
    public ResponseEntity<PagedResponse<FineractDtos.LoanProductResponse>> listLoanProducts(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        return ResponseEntity.ok(loanProductCatalogService.listCurrentTenantLoanProducts(page, size, sortBy, sortDir));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('LOAN_CREATE')")
    @Operation(summary = "Create a tenant loan product in Mini-LOS and Fineract")
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
    @Operation(summary = "Update a tenant loan product in Mini-LOS and Fineract by short name")
    public ResponseEntity<LoanProductCatalogDtos.LoanProductCatalogResponse> updateLoanProduct(
            @PathVariable String shortName,
            @Valid @RequestBody LoanProductCatalogDtos.UpdateLoanProductRequest request) {
        return ResponseEntity.ok(loanProductCatalogService.updateCurrentTenantProductByShortName(shortName, request));
    }
}
