package com.credvenn.lm.statement;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/integrations/cladfy")
@Tag(name = "Cladfy")
public class CladfyWebhookController {

    private final CladfyWebhookService cladfyWebhookService;

    public CladfyWebhookController(CladfyWebhookService cladfyWebhookService) {
        this.cladfyWebhookService = cladfyWebhookService;
    }

    @PostMapping("/webhook")
    @Operation(summary = "Receive Cladfy analysis completion webhook")
    public ResponseEntity<Void> webhook(@Valid @RequestBody CladfyDtos.WebhookRequest request) {
        cladfyWebhookService.handleWebhook(request);
        return ResponseEntity.ok().build();
    }
}
