package com.stockflow.seller.service;

import com.stockflow.common.exception.ConflictException;
import com.stockflow.common.exception.ResourceNotFoundException;
import com.stockflow.identity.domain.User;
import com.stockflow.inventory.dto.InventoryResponse;
import com.stockflow.inventory.repository.InventoryRepository;
import com.stockflow.order.domain.OrderItem;
import com.stockflow.order.dto.OrderItemResponse;
import com.stockflow.order.repository.OrderItemRepository;
import com.stockflow.seller.domain.Seller;
import com.stockflow.seller.dto.SellerProfileResponse;
import com.stockflow.seller.dto.UpdateSellerProfileRequest;
import com.stockflow.seller.repository.SellerRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SellerService {

    private final SellerRepository sellerRepository;
    private final InventoryRepository inventoryRepository;
    private final OrderItemRepository orderItemRepository;

    public SellerService(SellerRepository sellerRepository,
                         InventoryRepository inventoryRepository,
                         OrderItemRepository orderItemRepository) {
        this.sellerRepository = sellerRepository;
        this.inventoryRepository = inventoryRepository;
        this.orderItemRepository = orderItemRepository;
    }

    @Transactional(readOnly = true)
    public SellerProfileResponse getProfile(User user) {
        Seller seller = sellerRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Seller profile not found for user: " + user.getId()));
        return SellerProfileResponse.from(seller);
    }

    @Transactional
    public SellerProfileResponse updateProfile(UpdateSellerProfileRequest request, User user) {
        Seller seller = sellerRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Seller profile not found for user: " + user.getId()));

        if (!seller.getStoreName().equalsIgnoreCase(request.storeName().trim())
                && sellerRepository.existsByStoreName(request.storeName().trim())) {
            throw new ConflictException("Store name '" + request.storeName() + "' is already in use");
        }

        seller.updateDetails(request.storeName().trim(), request.businessEmail().trim());
        sellerRepository.save(seller);
        return SellerProfileResponse.from(seller);
    }

    @Transactional(readOnly = true)
    public Page<InventoryResponse> getSellerInventory(User user, Pageable pageable) {
        Seller seller = sellerRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Seller profile not found for user: " + user.getId()));
        return inventoryRepository.findBySellerId(seller.getId(), pageable).map(InventoryResponse::from);
    }

    @Transactional(readOnly = true)
    public List<OrderItemResponse> getSellerOrderItems(User user) {
        Seller seller = sellerRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Seller profile not found for user: " + user.getId()));
        return orderItemRepository.findBySellerId(seller.getId()).stream()
                .map(OrderItemResponse::from)
                .toList();
    }
}
