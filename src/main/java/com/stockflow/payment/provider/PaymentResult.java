package com.stockflow.payment.provider;

import com.stockflow.payment.domain.PaymentStatus;

public record PaymentResult(
        String providerReference,
        PaymentStatus status,
        String errorMessage
) {
    public static PaymentResult success(String providerReference) {
        return new PaymentResult(providerReference, PaymentStatus.SUCCEEDED, null);
    }

    public static PaymentResult pending(String providerReference) {
        return new PaymentResult(providerReference, PaymentStatus.PENDING, null);
    }

    public static PaymentResult failed(String providerReference, String errorMessage) {
        return new PaymentResult(providerReference, PaymentStatus.FAILED, errorMessage);
    }

    public static PaymentResult uncertain(String providerReference, String errorMessage) {
        return new PaymentResult(providerReference, PaymentStatus.UNCERTAIN, errorMessage);
    }
}
