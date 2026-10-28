package com.stockflow.payment.provider;

public interface PaymentProvider {
    String getProviderName();
    PaymentResult initiatePayment(PaymentRequest request);
    PaymentResult queryPaymentStatus(String providerReference);
    RefundResult initiateRefund(RefundRequest request);
    boolean verifyWebhookSignature(String payload, String signatureHeader);
    String generateSignature(String payload);
}
