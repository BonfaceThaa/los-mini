package com.credvenn.lm.payment;

import com.credvenn.lm.security.CurrentServiceActorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/service/payment-page")
@Tag(name = "Payment Page")
@SecurityRequirement(name = "bearerAuth")
public class PaymentPageController {

    private final MpesaPaymentPageService paymentPageService;
    private final CurrentServiceActorService currentServiceActorService;

    public PaymentPageController(
            MpesaPaymentPageService paymentPageService,
            CurrentServiceActorService currentServiceActorService) {
        this.paymentPageService = paymentPageService;
        this.currentServiceActorService = currentServiceActorService;
    }

    @GetMapping("/branding")
    @PreAuthorize("hasAuthority('SERVICE_TENANT_BRANDING_READ')")
    @Operation(summary = "Get tenant branding for the payment page")
    public ResponseEntity<PaymentDtos.PaymentPageBrandingResponse> getBranding() {
        var branding = paymentPageService.getBranding(currentServiceActorService.requireCurrentService());
        return ResponseEntity.ok(new PaymentDtos.PaymentPageBrandingResponse(
                branding.displayName(),
                branding.logoUrl(),
                branding.supportPhone(),
                branding.paymentInstructions()));
    }

    @PostMapping("/stk-push")
    @PreAuthorize("hasAuthority('SERVICE_STK_PUSH_INITIATE')")
    @Operation(summary = "Initiate a tenant STK push from the payment page")
    public ResponseEntity<PaymentDtos.PaymentPageAcknowledgementResponse> initiateStkPush(
            @Valid @RequestBody PaymentDtos.InitiatePaymentPageStkPushRequest request) {
        paymentPageService.initiateStkPush(
                currentServiceActorService.requireCurrentService(),
                request.phoneNumber());
        return ResponseEntity.ok(new PaymentDtos.PaymentPageAcknowledgementResponse("STK push request received."));
    }
}
