package com.stockflow.fulfilment;

import com.stockflow.common.exception.BusinessRuleViolationException;
import com.stockflow.fulfilment.domain.Shipment;
import com.stockflow.fulfilment.dto.CreateShipmentRequest;
import com.stockflow.fulfilment.dto.ShipmentResponse;
import com.stockflow.fulfilment.repository.ShipmentRepository;
import com.stockflow.fulfilment.service.FulfilmentService;
import com.stockflow.identity.domain.Role;
import com.stockflow.identity.domain.User;
import com.stockflow.order.domain.Order;
import com.stockflow.order.domain.OrderItem;
import com.stockflow.order.domain.OrderStatus;
import com.stockflow.order.repository.OrderRepository;
import com.stockflow.outbox.service.OutboxService;
import com.stockflow.seller.domain.Seller;
import com.stockflow.seller.domain.SellerStatus;
import com.stockflow.seller.repository.SellerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FulfilmentServiceTest {

    @Mock
    private ShipmentRepository shipmentRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private SellerRepository sellerRepository;

    @Mock
    private OutboxService outboxService;

    private FulfilmentService fulfilmentService;

    private User sellerUser;
    private Seller seller;
    private Order confirmedOrder;

    @BeforeEach
    void setUp() {
        fulfilmentService = new FulfilmentService(shipmentRepository, orderRepository, sellerRepository, outboxService);

        sellerUser = new User(UUID.randomUUID(), "seller@stockflow.com", "hash", "Seller", Role.SELLER);
        seller = new Seller(UUID.randomUUID(), sellerUser, "TechStore", "seller@stockflow.com", SellerStatus.ACTIVE);

        User customer = new User(UUID.randomUUID(), "buyer@stockflow.com", "hash", "Buyer", Role.CUSTOMER);
        confirmedOrder = new Order(UUID.randomUUID(), "ORD-555", customer, BigDecimal.valueOf(100), "USD", "123 Street");
        confirmedOrder.transitionTo(OrderStatus.CONFIRMED);

        OrderItem item = new OrderItem(UUID.randomUUID(), confirmedOrder, seller, UUID.randomUUID(), "SKU-TECH", "Gadget",
                BigDecimal.valueOf(100), 1, BigDecimal.ZERO, "USD");
        confirmedOrder.addItem(item);
    }

    @Test
    @DisplayName("Should successfully create shipment for confirmed order and transition order to SHIPPED")
    void shouldCreateShipmentForConfirmedOrder() {
        when(sellerRepository.findByUserId(sellerUser.getId())).thenReturn(Optional.of(seller));
        when(orderRepository.findById(confirmedOrder.getId())).thenReturn(Optional.of(confirmedOrder));
        when(shipmentRepository.save(any(Shipment.class))).thenAnswer(i -> i.getArgument(0));

        CreateShipmentRequest request = new CreateShipmentRequest(confirmedOrder.getId(), "FedEx", "TRK-98765");
        ShipmentResponse response = fulfilmentService.createShipment(request, sellerUser);

        assertThat(response).isNotNull();
        assertThat(response.carrier()).isEqualTo("FedEx");
        assertThat(response.trackingNumber()).isEqualTo("TRK-98765");
        assertThat(confirmedOrder.getStatus()).isEqualTo(OrderStatus.SHIPPED);

        verify(outboxService).publishEvent(eq("SHIPMENT_CREATED"), eq("Shipment"), any(), any());
    }

    @Test
    @DisplayName("Should reject shipment creation for unconfirmed/unpaid order")
    void shouldRejectShipmentForUnpaidOrder() {
        Order unpaidOrder = new Order(UUID.randomUUID(), "ORD-UNPAID", sellerUser, BigDecimal.TEN, "USD", "Addr");
        // Remains in PENDING_PAYMENT

        when(sellerRepository.findByUserId(sellerUser.getId())).thenReturn(Optional.of(seller));
        when(orderRepository.findById(unpaidOrder.getId())).thenReturn(Optional.of(unpaidOrder));

        CreateShipmentRequest request = new CreateShipmentRequest(unpaidOrder.getId(), "DHL", "TRK-000");

        assertThatThrownBy(() -> fulfilmentService.createShipment(request, sellerUser))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Order cannot be fulfilled in status PENDING_PAYMENT");
    }
}
