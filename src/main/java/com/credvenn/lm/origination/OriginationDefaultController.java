package com.credvenn.lm.origination;

import static com.credvenn.lm.origination.OriginationProfileDtos.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/tenant/origination-default")
@RequiredArgsConstructor @Tag(name = "Origination Profiles") @SecurityRequirement(name = "bearerAuth")
public class OriginationDefaultController {
    private final OriginationProfileService service;
    @GetMapping public DefaultResponse get() { return service.getDefault(); }
    @PutMapping public DefaultResponse set(@Valid @RequestBody DefaultRequest request) { return service.setDefault(request); }
}
