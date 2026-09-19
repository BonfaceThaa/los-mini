package com.credvenn.lm.origination;

import static com.credvenn.lm.origination.OriginationProfileDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.credvenn.lm.common.api.PagedResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;

@RestController @RequestMapping("/api/v1/origination-profiles")
@RequiredArgsConstructor @Tag(name = "Origination Profiles") @SecurityRequirement(name = "bearerAuth")
public class OriginationProfileController {
    private final OriginationProfileService service;
    @PostMapping @Operation(summary = "Create an inactive tenant origination profile")
    public ResponseEntity<ProfileResponse> create(@Valid @RequestBody CreateRequest request) {
        var result = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/origination-profiles/" + result.code())).body(result);
    }
    @GetMapping @Operation(summary = "List tenant origination profiles, optionally filtered by active status")
    public ProfileList list(@RequestParam(required = false) Boolean active) { return service.list(active); }
    @GetMapping("/{code}") public ProfileResponse get(@PathVariable String code) { return service.get(code); }
    @PatchMapping("/{code}") @Operation(summary = "Update a profile using its current technical version")
    public ProfileResponse update(@PathVariable String code, @Valid @RequestBody UpdateRequest request) { return service.update(code, request); }
    @DeleteMapping("/{code}") @Operation(summary = "Deactivate a profile; historical data is retained")
    public ResponseEntity<Void> deactivate(@PathVariable String code, @RequestParam long expectedVersion) {
        service.deactivate(code, expectedVersion);
        return ResponseEntity.noContent().build();
    }
    @GetMapping("/{code}/history") public PagedResponse<AuditResponse> history(@PathVariable String code,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return PagedResponse.fromPage(service.history(code, page, size), "createdAt", "desc");
    }
}
