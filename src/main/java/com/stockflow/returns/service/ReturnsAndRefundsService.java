package com.stockflow.returns.service;

import com.stockflow.common.exception.BusinessRuleViolationException;
import com.stockflow.common.exception.ResourceNotFoundException;
import com.stockflow.common.exception.UnauthorizedException;
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
import com.stockflow.payment.provider.RefundRequest;
import com.stockflow.payment.provider.RefundResult;
import com.stockflow.payment.repository.PaymentRepository;
import com.stockflow.returns.domain.Refund;
import com.stockflow.returns.domain.RestockDecision;
import com.stockflow.returns.domain.ReturnRequest;
import com.stockflow.returns.domain.ReturnStatus;
import com.stockflow.returns.dto.*;
import com.stockflow.returns.repository.RefundRepository;
import com.stockflow.returns.repository.ReturnRequestRepository;
import com.stockflow.seller.domain.Seller;
import com.stockflow.seller.repository.SellerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ReturnsAndRefundsService {

    private static final Logger log = LoggerFactory.getLogger(ReturnsAndRefundsService.class);

    private final ReturnRequestRepository returnRepository;
    private final RefundRepository refundRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final SellerRepository sellerRepository;
    private final InventoryService inventoryService;
    private final PaymentProvider paymentProvider;
    private final OutboxService outboxService;

    public ReturnsAndRefundsService(ReturnRequestRepository returnRepository,
                                   RefundRepository refundRepository,
                                   OrderRepository orderRepository,
                                   PaymentRepository paymentRepository,
                                   SellerRepository sellerRepository,
                                   InventoryService inventoryService,
                                   PaymentProvider paymentProvider,
                                   OutboxService outboxService) {
        this.returnRepository = returnRepository;
        this.refundRepository = refundRepository;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.sellerRepository = sellerRepository;
        this.inventoryService = inventoryService;
        this.paymentProvider = paymentProvider;
        this.outboxService = outboxService;
    }

    @Transactional
    public ReturnResponse requestReturn(CreateReturnRequest request, User customer) {
        Order order = orderRepository.findById(request.orderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + request.orderId()));

        if (!order.getCustomer().getId().equals(customer.getId())) {
            throw new UnauthorizedException("You are not authorized to request return for this order");
        }

        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new BusinessRuleViolationException("Returns can only be requested for DELIVERED orders. Current status: " + order.getStatus());
        }

        // Pick primary seller from items
        Seller primarySeller = order.getItems().getFirst().getSeller();

        ReturnRequest returnReq = new ReturnRequest(
                UUID.randomUUID(),
                order,
                customer,
                primarySeller,
                request.reason().trim()
        );
        returnReq = returnRepository.save(returnReq);

        outboxService.publishEvent(
                "RETURN_REQUESTED",
                "ReturnRequest",
                returnReq.getId().toString(),
                Map.of("returnId", returnReq.getId(), "orderId", order.getId(), "reason", returnReq.getReason())
        );

        log.info("Return requested for order {} by customer {}", order.getOrderNumber(), customer.getEmail());
        return ReturnResponse.from(returnReq);
    }

    @Transactional
    public ReturnResponse decideReturn(UUID returnId, ReturnDecisionRequest request, User sellerUser) {
        ReturnRequest returnReq = returnRepository.findById(returnId)
                .orElseThrow(() -> new ResourceNotFoundException("Return request not found: " + returnId));

        validateSellerOwnership(returnReq.getSeller(), sellerUser);

        if (returnReq.getStatus() != ReturnStatus.REQUESTED) {
            throw new BusinessRuleViolationException("Cannot decide on return in status " + returnReq.getStatus());
        }

        if (Boolean.TRUE.equals(request.approved())) {
            returnReq.approve();
        } else {
            returnReq.reject();
        }
        returnRepository.save(returnReq);

        log.info("Seller {} decided return {}: approved={}", sellerUser.getEmail(), returnId, request.approved());
        return ReturnResponse.from(returnReq);
    }

    @Transactional
    public ReturnResponse receiveReturn(UUID returnId, ReceiveReturnRequest request, User sellerUser) {
        ReturnRequest returnReq = returnRepository.findById(returnId)
                .orElseThrow(() -> new ResourceNotFoundException("Return request not found: " + returnId));

        validateSellerOwnership(returnReq.getSeller(), sellerUser);

        if (returnReq.getStatus() != ReturnStatus.APPROVED) {
            throw new BusinessRuleViolationException("Return must be APPROVED before receipt. Status: " + returnReq.getStatus());
        }

        returnReq.receive(request.restockDecision());
        returnRepository.save(returnReq);

        // Explicit restock policy decision
        if (request.restockDecision() == RestockDecision.RESTOCK) {
            for (OrderItem item : returnReq.getOrder().getItems()) {
                if (item.getSeller().getId().equals(returnReq.getSeller().getId())) {
                    inventoryService.restockReturnedInventory(
                            item.getSku(),
                            item.getQuantity(),
                            returnReq.getId().toString(),
                            sellerUser.getEmail()
                    );
                }
            }
            log.info("Restocked inventory for approved return {}", returnId);
        } else {
            log.info("Return {} items routed to {} (inventory was not restocked to active pool)",
                    returnId, request.restockDecision());
        }

        return ReturnResponse.from(returnReq);
    }

    @Transactional
    public RefundResponse issueRefund(IssueRefundRequest request, User actor, boolean isAdmin) {
        Order order = orderRepository.findById(request.orderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + request.orderId()));

        List<Payment> succeededPayments = paymentRepository.findByOrderId(order.getId()).stream()
                .filter(p -> p.getStatus() == PaymentStatus.SUCCEEDED)
                .toList();

        if (succeededPayments.isEmpty()) {
            throw new BusinessRuleViolationException("Cannot refund order without confirmed payment");
        }
        Payment payment = succeededPayments.getFirst();

        // Financial invariant: Total refunded cannot exceed order amount
        BigDecimal totalRefunded = refundRepository.calculateTotalRefundedAmount(order.getId());
        BigDecimal remainingRefundable = order.getTotalAmount().subtract(totalRefunded);

        if (request.amount().compareTo(remainingRefundable) > 0) {
            throw new BusinessRuleViolationException(
                    "Refund amount (" + request.amount() + ") exceeds remaining refundable balance (" + remainingRefundable + ")");
        }

        // Invoke payment gateway refund
        RefundRequest refundReq = new RefundRequest(
                payment.getId(),
                payment.getProviderReference(),
                request.amount(),
                order.getCurrency(),
                request.reason()
        );
        RefundResult refundResult = paymentProvider.initiateRefund(refundReq);

        if (!refundResult.success()) {
            throw new BusinessRuleViolationException("Payment gateway rejected refund: " + refundResult.errorMessage());
        }

        Refund refund = new Refund(
                UUID.randomUUID(),
                order,
                payment,
                request.amount(),
                order.getCurrency(),
                request.reason().trim(),
                refundResult.providerRefundId()
        );
        refund = refundRepository.save(refund);

        // If fully refunded, transition order to REFUNDED
        BigDecimal newTotalRefunded = totalRefunded.add(request.amount());
        if (newTotalRefunded.compareTo(order.getTotalAmount()) >= 0) {
            order.transitionTo(OrderStatus.REFUNDED);
            orderRepository.save(order);
        }

        outboxService.publishEvent(
                "REFUND_COMPLETED",
                "Refund",
                refund.getId().toString(),
                Map.of("orderId", order.getId(), "amount", refund.getAmount(), "refundId", refund.getId())
        );

        log.info("Issued refund {} for order {}: amount {} {}",
                refund.getId(), order.getOrderNumber(), refund.getAmount(), refund.getCurrency());

        return RefundResponse.from(refund);
    }

    @Transactional(readOnly = true)
    public List<ReturnResponse> getCustomerReturns(User customer) {
        return returnRepository.findByCustomerId(customer.getId()).stream()
                .map(ReturnResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReturnResponse> getSellerReturns(User sellerUser) {
        if (sellerUser.getRole() == Role.ADMIN) {
            return returnRepository.findAll().stream()
                    .map(ReturnResponse::from)
                    .toList();
        }
        Seller seller = sellerRepository.findByUserId(sellerUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Seller profile not found for user: " + sellerUser.getId()));
        return returnRepository.findBySellerId(seller.getId()).stream()
                .map(ReturnResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReturnResponse> getOrderReturns(UUID orderId, User user) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        boolean isAdmin = user.getRole() == Role.ADMIN;
        boolean isOwner = order.getCustomer().getId().equals(user.getId());
        if (!isAdmin && !isOwner) {
            throw new UnauthorizedException("You are not authorized to view return requests for this order");
        }
        return returnRepository.findByOrderId(orderId).stream()
                .map(ReturnResponse::from)
                .toList();
    }

    private void validateSellerOwnership(Seller seller, User sellerUser) {
        if (sellerUser.getRole() != Role.ADMIN && !seller.getUser().getId().equals(sellerUser.getId())) {
            throw new UnauthorizedException("You are not authorized to manage returns for another seller's store");
        }
    }
}
