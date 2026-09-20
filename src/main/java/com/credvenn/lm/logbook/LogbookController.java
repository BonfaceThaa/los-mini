package com.credvenn.lm.logbook;

import static com.credvenn.lm.logbook.LogbookDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/v1/applications/{applicationId}/logbook")
@Tag(name="Logbook Capabilities") @SecurityRequirement(name="bearerAuth")
public class LogbookController {
    private final LogbookService service;
    @GetMapping @Operation(summary="Read vehicle, evidence history and current LTV readiness", description="Requires LOGBOOK_VIEW. Tenant is derived from authentication. Capability readiness is not loan approval.")
    public Summary get(@PathVariable String applicationId) { return service.get(applicationId); }
    @PutMapping("/vehicle") @Operation(summary="Capture or replace vehicle details", description="Requires LOGBOOK_VEHICLE_MANAGE. Replacement invalidates previous evidence; stale expectedVersion returns 409.")
    public Summary vehicle(@PathVariable String applicationId, @Valid @RequestBody VehicleRequest request) { return service.saveVehicle(applicationId, request); }
    @PostMapping("/valuations") @Operation(summary="Submit a valuer's report and KES vehicle values", description="Requires LOGBOOK_VALUATION_SUBMIT. A new submission supersedes prior valuations for readiness until independently approved.")
    public Summary valuation(@PathVariable String applicationId, @Valid @RequestBody ValuationRequest request) { return service.submitValuation(applicationId, request); }
    @PostMapping("/valuations/{valuationId}/review") @Operation(summary="Approve, reject or revoke the latest valuation", description="Requires LOGBOOK_VALUATION_REVIEW. The submitting user cannot review their own valuation. Approved valuations may only be revoked; all decisions are audited.")
    public Summary review(@PathVariable String applicationId, @PathVariable String valuationId, @Valid @RequestBody ReviewRequest request) { return service.reviewValuation(applicationId, valuationId, request); }
    @PostMapping("/verifications/{kind}") @Operation(summary="Record ownership, insurance or security-registration evidence", description="Requires LOGBOOK_VERIFICATION_MANAGE. Append-only staff attestation, not a registry integration. Latest decision supersedes prior evidence.")
    public Summary verify(@PathVariable String applicationId, @PathVariable VerificationKind kind, @Valid @RequestBody VerificationRequest request) { return service.verify(applicationId, kind, request); }
    @GetMapping("/audit") @Operation(summary="Read logbook change and review history", description="Requires LOGBOOK_VIEW.")
    public List<LogbookAudit> audit(@PathVariable String applicationId) { return service.auditHistory(applicationId); }
}
