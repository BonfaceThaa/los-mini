package com.credvenn.lm.loanproduct;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.credvenn.lm.common.exception.ApiError;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/loan-products")
@RequiredArgsConstructor
@Tag(name = "Fineract Loan Products")
@SecurityRequirement(name = "bearerAuth")
public class ProductOriginationController {
    private final ProductOriginationService service;
    @Schema(name = "AssociateProductOriginationProfileRequest", description = "Associate a tenant product with an existing tenant profile. Explicit draft profiles are allowed.")
    public record AssociateProfileRequest(
            @NotBlank @Size(max = 100) @Schema(example = "LOGBOOK", description = "Tenant-local profile code; required, no default fallback on this endpoint") String originationProfileCode) {}

    @Operation(summary = "Associate a loan product with an origination profile",
            description = "Requires LOAN_PRODUCT_UPDATE. Changes only the local association; makes no Fineract call. Repeating the same association is idempotent. A referenced product cannot move to another profile. Unclassified historical products require compatible application profiles.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Updated or unchanged product mapping", content = @Content(schema = @Schema(implementation = LoanProductCatalogDtos.LoanProductCatalogResponse.class))),
        @ApiResponse(responseCode = "400", description = "Missing or invalid profile code", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "Missing LOAN_PRODUCT_UPDATE permission or tenant context", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "Product or profile does not exist in the authenticated tenant", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "Existing application selections prevent reassignment", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PutMapping("/{productCode}/origination-profile")
    @PreAuthorize("hasAuthority('LOAN_PRODUCT_UPDATE')")
    public LoanProductCatalogDtos.LoanProductCatalogResponse associate(@Parameter(description = "Tenant-local product code, not the product short name", example = "LOGBOOK_12_MONTHS") @PathVariable String productCode,
            @Valid @RequestBody AssociateProfileRequest request) {
        return service.associate(productCode, request.originationProfileCode());
    }
}
