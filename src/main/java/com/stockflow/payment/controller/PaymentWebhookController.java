package com.stockflow.payment.controller;

import com.stockflow.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments/webhooks")
@Tag(name = "Payment Webhooks", description = "Signature-Verified Webhook Ingestion")
public class PaymentWebhookController {

    private final PaymentService paymentService;

    public PaymentWebhookController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    @Operation(summary = "Ingest provider webhook with HMAC-SHA256 signature verification and deduplication")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String rawPayload,
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signatureHeader) {
        paymentService.processWebhook(rawPayload, signatureHeader);
        return ResponseEntity.ok("Webhook processed successfully");
    }
}
