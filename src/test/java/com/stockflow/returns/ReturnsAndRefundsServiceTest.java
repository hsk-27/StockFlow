package com.stockflow.returns;

import com.stockflow.common.exception.BusinessRuleViolationException;
import com.stockflow.identity.domain.Role;
import com.stockflow.identity.domain.User;
import com.stockflow.inventory.service.InventoryService;
import com.stockflow.order.domain.Order;
import com.stockflow.order.domain.OrderItem;
import com.stockflow.order.domain.OrderStatus;
import com.stockflow.order.repository.OrderRepository;
import com.stockflow.outbox.service.OutboxService;
import com.stockflow.payment.domain.Payment;
import com.stockflow.payment.domain.PaymentStatus;
import com.stockflow.payment.provider.PaymentProvider;
import com.stockflow.payment.provider.RefundResult;
import com.stockflow.payment.repository.PaymentRepository;
import com.stockflow.returns.domain.Refund;
import com.stockflow.returns.domain.RestockDecision;
import com.stockflow.returns.domain.ReturnRequest;
import com.stockflow.returns.dto.CreateReturnRequest;
import com.stockflow.returns.dto.IssueRefundRequest;
import com.stockflow.returns.dto.ReceiveReturnRequest;
import com.stockflow.returns.dto.RefundResponse;
import com.stockflow.returns.repository.RefundRepository;
import com.stockflow.returns.repository.ReturnRequestRepository;
import com.stockflow.returns.service.ReturnsAndRefundsService;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReturnsAndRefundsServiceTest {

    @Mock
    private ReturnRequestRepository returnRepository;

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private SellerRepository sellerRepository;

    @Mock
    private InventoryService inventoryService;

    @Mock
    private PaymentProvider paymentProvider;

    @Mock
    private OutboxService outboxService;

    private ReturnsAndRefundsService service;

    private User customer;
    private User sellerUser;
    private Seller seller;
    private Order deliveredOrder;
    private Payment payment;

    @BeforeEach
    void setUp() {
        service = new ReturnsAndRefundsService(returnRepository, refundRepository, orderRepository,
                paymentRepository, sellerRepository, inventoryService, paymentProvider, outboxService);

        customer = new User(UUID.randomUUID(), "buyer@stockflow.com", "hash", "Buyer", Role.CUSTOMER);
        sellerUser = new User(UUID.randomUUID(), "seller@stockflow.com", "hash", "Seller", Role.SELLER);
        seller = new Seller(UUID.randomUUID(), sellerUser, "TechStore", "seller@stockflow.com", SellerStatus.ACTIVE);

        deliveredOrder = new Order(UUID.randomUUID(), "ORD-DELIVERED", customer, BigDecimal.valueOf(100), "USD", "100 St");
        deliveredOrder.transitionTo(OrderStatus.CONFIRMED);
        deliveredOrder.transitionTo(OrderStatus.PROCESSING);
        deliveredOrder.transitionTo(OrderStatus.SHIPPED);
        deliveredOrder.transitionTo(OrderStatus.DELIVERED);

        OrderItem item = new OrderItem(UUID.randomUUID(), deliveredOrder, seller, UUID.randomUUID(), "SKU-TECH", "Gadget",
                BigDecimal.valueOf(100), 1, BigDecimal.ZERO, "USD");
        deliveredOrder.addItem(item);

        payment = new Payment(UUID.randomUUID(), deliveredOrder, "MOCK", BigDecimal.valueOf(100), "USD");
        payment.markSucceeded("REF-123");
    }

    @Test
    @DisplayName("Should reject return request if order is not DELIVERED")
    void shouldRejectReturnForUndeliveredOrder() {
        Order pendingOrder = new Order(UUID.randomUUID(), "ORD-PEND", customer, BigDecimal.TEN, "USD", "Addr");
        when(orderRepository.findById(pendingOrder.getId())).thenReturn(Optional.of(pendingOrder));

        CreateReturnRequest request = new CreateReturnRequest(pendingOrder.getId(), "Defective item");

        assertThatThrownBy(() -> service.requestReturn(request, customer))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Returns can only be requested for DELIVERED orders");
    }

    @Test
    @DisplayName("Restock Policy: RESTOCK decision triggers inventory restock; SCRAP does not")
    void shouldFollowRestockDecisionPolicy() {
        ReturnRequest returnReq = new ReturnRequest(UUID.randomUUID(), deliveredOrder, customer, seller, "Defect");
        returnReq.approve();

        when(returnRepository.findById(returnReq.getId())).thenReturn(Optional.of(returnReq));

        // 1. Receive with RESTOCK
        ReceiveReturnRequest restockRequest = new ReceiveReturnRequest(RestockDecision.RESTOCK);
        service.receiveReturn(returnReq.getId(), restockRequest, sellerUser);
        verify(inventoryService).restockReturnedInventory(eq("SKU-TECH"), eq(1), eq(returnReq.getId().toString()), eq(sellerUser.getEmail()));

        // 2. Receive with SCRAP: inventoryService must NOT be called again
        clearInvocations(inventoryService);
        returnReq.approve();
        ReceiveReturnRequest scrapRequest = new ReceiveReturnRequest(RestockDecision.SCRAP);
        service.receiveReturn(returnReq.getId(), scrapRequest, sellerUser);
        verifyNoInteractions(inventoryService);
    }

    @Test
    @DisplayName("Financial Invariant: Refund amount cannot exceed remaining refundable balance")
    void shouldRejectRefundExceedingOrderBalance() {
        when(orderRepository.findById(deliveredOrder.getId())).thenReturn(Optional.of(deliveredOrder));
        when(paymentRepository.findByOrderId(deliveredOrder.getId())).thenReturn(List.of(payment));
        when(refundRepository.calculateTotalRefundedAmount(deliveredOrder.getId())).thenReturn(BigDecimal.valueOf(80)); // 80 already refunded

        // Attempting to refund $30 when only $20 remains on a $100 order!
        IssueRefundRequest request = new IssueRefundRequest(deliveredOrder.getId(), BigDecimal.valueOf(30), "Overcharge");

        assertThatThrownBy(() -> service.issueRefund(request, sellerUser, false))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("exceeds remaining refundable balance");
    }

    @Test
    @DisplayName("Should successfully issue refund within refundable balance")
    void shouldIssueRefundWithinBalance() {
        when(orderRepository.findById(deliveredOrder.getId())).thenReturn(Optional.of(deliveredOrder));
        when(paymentRepository.findByOrderId(deliveredOrder.getId())).thenReturn(List.of(payment));
        when(refundRepository.calculateTotalRefundedAmount(deliveredOrder.getId())).thenReturn(BigDecimal.ZERO);
        when(paymentProvider.initiateRefund(any())).thenReturn(RefundResult.success("REFUND-EXT-1"));
        when(refundRepository.save(any(Refund.class))).thenAnswer(i -> i.getArgument(0));

        IssueRefundRequest request = new IssueRefundRequest(deliveredOrder.getId(), BigDecimal.valueOf(50), "Goodwill");
        RefundResponse response = service.issueRefund(request, sellerUser, false);

        assertThat(response.amount()).isEqualByComparingTo("50.00");
        assertThat(response.providerRefundId()).isEqualTo("REFUND-EXT-1");
        verify(outboxService).publishEvent(eq("REFUND_COMPLETED"), eq("Refund"), any(), any());
    }
}
