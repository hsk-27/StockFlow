package com.stockflow.payment.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MockPaymentProvider implements PaymentProvider {

    private static final Logger log = LoggerFactory.getLogger(MockPaymentProvider.class);

    private final String webhookSecret;
    private final Map<String, PaymentResult> simulatedLedger = new ConcurrentHashMap<>();

    // Test simulation switches
    private volatile boolean simulateTimeout = false;
    private volatile boolean simulateFailure = false;

    public MockPaymentProvider(@Value("${stockflow.payment.webhook-secret:whsec_test_mock_secret_key_12345}") String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    @Override
    public String getProviderName() {
        return "MOCK_GATEWAY";
    }

    @Override
    public PaymentResult initiatePayment(PaymentRequest request) {
        String reference = "PAY-MOCK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        if (simulateTimeout) {
            log.warn("MockPaymentProvider: Simulating network timeout/gateway disconnect for order {}", request.orderId());
            PaymentResult uncertainResult = PaymentResult.uncertain(reference, "Gateway read timeout. Outcome uncertain.");
            simulatedLedger.put(reference, uncertainResult);
            return uncertainResult;
        }

        if (simulateFailure) {
            log.info("MockPaymentProvider: Simulating payment decline for order {}", request.orderId());
            PaymentResult failedResult = PaymentResult.failed(reference, "Card declined: Insufficient funds");
            simulatedLedger.put(reference, failedResult);
            return failedResult;
        }

        log.info("MockPaymentProvider: Payment successfully initiated with ref {}", reference);
        PaymentResult successResult = PaymentResult.success(reference);
        simulatedLedger.put(reference, successResult);
        return successResult;
    }

    @Override
    public PaymentResult queryPaymentStatus(String providerReference) {
        PaymentResult existing = simulatedLedger.get(providerReference);
        if (existing != null) {
            // Reconcile uncertain to succeeded if investigated
            if (existing.status() == com.stockflow.payment.domain.PaymentStatus.UNCERTAIN) {
                PaymentResult reconciled = PaymentResult.success(providerReference);
                simulatedLedger.put(providerReference, reconciled);
                return reconciled;
            }
            return existing;
        }
        return PaymentResult.success(providerReference);
    }

    @Override
    public RefundResult initiateRefund(RefundRequest request) {
        String refundRef = "REF-MOCK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.info("MockPaymentProvider: Refund executed for amount {} {} with refund id {}",
                request.amount(), request.currency(), refundRef);
        return RefundResult.success(refundRef);
    }

    @Override
    public boolean verifyWebhookSignature(String payload, String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            return false;
        }
        String computedSignature = generateSignature(payload);
        return MessageDigestComparison(computedSignature, signatureHeader);
    }

    @Override
    public String generateSignature(String payload) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            hmac.init(secretKey);
            byte[] bytes = hmac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to calculate HMAC signature", e);
        }
    }

    private boolean MessageDigestComparison(String a, String b) {
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        return java.security.MessageDigest.isEqual(aBytes, bBytes);
    }

    // Test control helpers
    public void setSimulateTimeout(boolean simulateTimeout) {
        this.simulateTimeout = simulateTimeout;
    }

    public void setSimulateFailure(boolean simulateFailure) {
        this.simulateFailure = simulateFailure;
    }
}
