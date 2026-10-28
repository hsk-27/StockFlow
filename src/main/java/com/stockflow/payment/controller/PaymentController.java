package com.stockflow.payment.controller;

import com.stockflow.identity.domain.User;
import com.stockflow.payment.dto.InitiatePaymentRequest;
import com.stockflow.payment.dto.PaymentResponse;
import com.stockflow.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payments", description = "Payment Initiation & Gateway Operations")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/initiate")
    @Operation(summary = "Initiate payment for an eligible order (non-blocking gateway invocation)")
    public ResponseEntity<PaymentResponse> initiatePayment(
            @Valid @RequestBody InitiatePaymentRequest request,
            @AuthenticationPrincipal User user) {
        PaymentResponse response = paymentService.initiatePayment(request.orderId(), user);
        return ResponseEntity.ok(response);
    }
}
