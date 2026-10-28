package com.stockflow.payment;

import com.stockflow.payment.domain.PaymentStatus;
import com.stockflow.payment.provider.MockPaymentProvider;
import com.stockflow.payment.provider.PaymentRequest;
import com.stockflow.payment.provider.PaymentResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MockPaymentProviderTest {

    private MockPaymentProvider provider;

    @BeforeEach
    void setUp() {
        provider = new MockPaymentProvider("test-secret-12345");
    }

    @Test
    @DisplayName("Should successfully verify valid HMAC-SHA256 webhook signature")
    void shouldVerifyValidSignature() {
        String payload = "{\"eventId\":\"evt_123\",\"status\":\"success\"}";
        String signature = provider.generateSignature(payload);

        assertThat(provider.verifyWebhookSignature(payload, signature)).isTrue();
    }

    @Test
    @DisplayName("Should reject invalid or tampered webhook signature")
    void shouldRejectTamperedSignature() {
        String payload = "{\"eventId\":\"evt_123\",\"status\":\"success\"}";
        String forgedSignature = "abcdef0123456789";

        assertThat(provider.verifyWebhookSignature(payload, forgedSignature)).isFalse();
    }

    @Test
    @DisplayName("Should return UNCERTAIN outcome when network timeout simulation is enabled")
    void shouldSimulateNetworkTimeout() {
        provider.setSimulateTimeout(true);
        PaymentRequest request = new PaymentRequest(UUID.randomUUID(), "ORD-1", BigDecimal.TEN, "USD", "c@c.com", "key-1");

        PaymentResult result = provider.initiatePayment(request);

        assertThat(result.status()).isEqualTo(PaymentStatus.UNCERTAIN);
        assertThat(result.errorMessage()).contains("Gateway read timeout");
    }

    @Test
    @DisplayName("Should return FAILED outcome when card decline simulation is enabled")
    void shouldSimulatePaymentDecline() {
        provider.setSimulateFailure(true);
        PaymentRequest request = new PaymentRequest(UUID.randomUUID(), "ORD-2", BigDecimal.TEN, "USD", "c@c.com", "key-2");

        PaymentResult result = provider.initiatePayment(request);

        assertThat(result.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(result.errorMessage()).contains("Card declined");
    }
}
