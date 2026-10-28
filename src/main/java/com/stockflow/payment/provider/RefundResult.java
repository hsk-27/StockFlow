package com.stockflow.payment.provider;

public record RefundResult(
        String providerRefundId,
        boolean success,
        String errorMessage
) {
    public static RefundResult success(String providerRefundId) {
        return new RefundResult(providerRefundId, true, null);
    }

    public static RefundResult failure(String errorMessage) {
        return new RefundResult(null, false, errorMessage);
    }
}
