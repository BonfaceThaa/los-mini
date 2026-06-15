package com.credvenn.lm.fineract;

import com.credvenn.lm.common.api.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
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
@RequestMapping("/api/v1/clients")
@Tag(name = "Fineract Clients")
@SecurityRequirement(name = "bearerAuth")
public class FineractClientController {

    private final FineractClientService fineractClientService;

    public FineractClientController(FineractClientService fineractClientService) {
        this.fineractClientService = fineractClientService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CLIENT_VIEW')")
    @Operation(summary = "List Fineract clients for the authenticated tenant")
    public ResponseEntity<PagedResponse<FineractDtos.FineractClientResponse>> listClients(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(defaultValue = "displayName") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {
        return ResponseEntity.ok(fineractClientService.listCurrentTenantClients(page, size, sortBy, sortDir));
    }

    @GetMapping("/{clientId}")
    @PreAuthorize("hasAuthority('CLIENT_VIEW')")
    @Operation(summary = "Get a Fineract client for the authenticated tenant")
    public ResponseEntity<FineractDtos.FineractClientResponse> getClient(@PathVariable String clientId) {
        return ResponseEntity.ok(fineractClientService.getCurrentTenantClient(clientId));
    }
}
