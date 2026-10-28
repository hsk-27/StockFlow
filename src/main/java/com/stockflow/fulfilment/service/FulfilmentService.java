package com.stockflow.fulfilment.service;

import com.stockflow.common.exception.BusinessRuleViolationException;
import com.stockflow.common.exception.ResourceNotFoundException;
import com.stockflow.common.exception.UnauthorizedException;
import com.stockflow.fulfilment.domain.Shipment;
import com.stockflow.fulfilment.dto.CreateShipmentRequest;
import com.stockflow.fulfilment.dto.ShipmentResponse;
import com.stockflow.fulfilment.repository.ShipmentRepository;
import com.stockflow.identity.domain.Role;
import com.stockflow.identity.domain.User;
import com.stockflow.order.domain.Order;
import com.stockflow.order.domain.OrderStatus;
import com.stockflow.order.repository.OrderRepository;
import com.stockflow.outbox.service.OutboxService;
import com.stockflow.seller.domain.Seller;
import com.stockflow.seller.repository.SellerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class FulfilmentService {

    private static final Logger log = LoggerFactory.getLogger(FulfilmentService.class);

    private final ShipmentRepository shipmentRepository;
    private final OrderRepository orderRepository;
    private final SellerRepository sellerRepository;
    private final OutboxService outboxService;

    public FulfilmentService(ShipmentRepository shipmentRepository,
                             OrderRepository orderRepository,
                             SellerRepository sellerRepository,
                             OutboxService outboxService) {
        this.shipmentRepository = shipmentRepository;
        this.orderRepository = orderRepository;
        this.sellerRepository = sellerRepository;
        this.outboxService = outboxService;
    }

    @Transactional
    public ShipmentResponse createShipment(CreateShipmentRequest request, User sellerUser) {
        Seller seller = sellerRepository.findByUserId(sellerUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Seller profile not found for user: " + sellerUser.getId()));

        Order order = orderRepository.findById(request.orderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + request.orderId()));

        // Enforce that order is paid and eligible for shipment
        if (order.getStatus() != OrderStatus.CONFIRMED && order.getStatus() != OrderStatus.PROCESSING) {
            throw new BusinessRuleViolationException(
                    "Order cannot be fulfilled in status " + order.getStatus() + ". Must be CONFIRMED or PROCESSING.");
        }

        // Verify seller is part of this order
        boolean sellerIsParticipant = order.getItems().stream()
                .anyMatch(item -> item.getSeller().getId().equals(seller.getId()));

        if (!sellerIsParticipant && sellerUser.getRole() != Role.ADMIN) {
            throw new UnauthorizedException("You have no items in this order to fulfill");
        }

        Shipment shipment = new Shipment(
                UUID.randomUUID(),
                order,
                seller,
                request.carrier().trim(),
                request.trackingNumber().trim()
        );
        shipment = shipmentRepository.save(shipment);

        // Transition order state to SHIPPED
        order.transitionTo(OrderStatus.SHIPPED);
        orderRepository.save(order);

        outboxService.publishEvent(
                "SHIPMENT_CREATED",
                "Shipment",
                shipment.getId().toString(),
                Map.of(
                        "orderId", order.getId(),
                        "carrier", shipment.getCarrier(),
                        "trackingNumber", shipment.getTrackingNumber()
                )
        );

        log.info("Shipment created for order {}: Carrier {}, Tracking {}",
                order.getOrderNumber(), shipment.getCarrier(), shipment.getTrackingNumber());

        return ShipmentResponse.from(shipment);
    }

    @Transactional
    public ShipmentResponse confirmDelivery(UUID shipmentId, User user) {
        Shipment shipment = shipmentRepository.findById(shipmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Shipment not found: " + shipmentId));

        Order order = shipment.getOrder();

        shipment.markDelivered();
        shipmentRepository.save(shipment);

        order.transitionTo(OrderStatus.DELIVERED);
        orderRepository.save(order);

        outboxService.publishEvent(
                "SHIPMENT_DELIVERED",
                "Shipment",
                shipment.getId().toString(),
                Map.of("orderId", order.getId(), "deliveredAt", shipment.getDeliveredAt())
        );

        log.info("Delivery confirmed for shipment {} of order {}", shipmentId, order.getOrderNumber());
        return ShipmentResponse.from(shipment);
    }

    @Transactional(readOnly = true)
    public List<ShipmentResponse> getShipmentsForOrder(UUID orderId) {
        return shipmentRepository.findByOrderId(orderId).stream()
                .map(ShipmentResponse::from)
                .toList();
    }
}
