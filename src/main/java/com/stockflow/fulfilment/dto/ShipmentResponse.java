package com.stockflow.fulfilment.dto;

import com.stockflow.fulfilment.domain.Shipment;
import com.stockflow.fulfilment.domain.ShipmentStatus;

import java.time.Instant;
import java.util.UUID;

public record ShipmentResponse(
        UUID id,
        UUID orderId,
        UUID sellerId,
        String storeName,
        String carrier,
        String trackingNumber,
        ShipmentStatus status,
        Instant shippedAt,
        Instant deliveredAt,
        Instant createdAt
) {
    public static ShipmentResponse from(Shipment shipment) {
        return new ShipmentResponse(
                shipment.getId(),
                shipment.getOrder().getId(),
                shipment.getSeller().getId(),
                shipment.getSeller().getStoreName(),
                shipment.getCarrier(),
                shipment.getTrackingNumber(),
                shipment.getStatus(),
                shipment.getShippedAt(),
                shipment.getDeliveredAt(),
                shipment.getCreatedAt()
        );
    }
}
