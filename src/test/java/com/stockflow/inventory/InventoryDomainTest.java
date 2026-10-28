package com.stockflow.inventory;

import com.stockflow.catalog.domain.Product;
import com.stockflow.common.exception.BusinessRuleViolationException;
import com.stockflow.identity.domain.Role;
import com.stockflow.identity.domain.User;
import com.stockflow.inventory.domain.Inventory;
import com.stockflow.seller.domain.Seller;
import com.stockflow.seller.domain.SellerStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryDomainTest {

    private Inventory inventory;

    @BeforeEach
    void setUp() {
        User user = new User(UUID.randomUUID(), "seller@stockflow.com", "hash", "Seller", Role.SELLER);
        Seller seller = new Seller(UUID.randomUUID(), user, "Apex", "seller@stockflow.com", SellerStatus.ACTIVE);
        Product product = new Product(UUID.randomUUID(), seller, null, "Item", "Desc", BigDecimal.valueOf(100), "USD");
        inventory = new Inventory(UUID.randomUUID(), "SKU-TEST-001", product, seller, 10);
    }

    @Test
    @DisplayName("Should correctly calculate available quantity")
    void shouldCalculateAvailableQuantity() {
        assertThat(inventory.getOnHand()).isEqualTo(10);
        assertThat(inventory.getReserved()).isEqualTo(0);
        assertThat(inventory.getAvailableQuantity()).isEqualTo(10);

        inventory.reserve(4);
        assertThat(inventory.getOnHand()).isEqualTo(10);
        assertThat(inventory.getReserved()).isEqualTo(4);
        assertThat(inventory.getAvailableQuantity()).isEqualTo(6);
    }

    @Test
    @DisplayName("Should reject reservation exceeding available quantity (prevent overselling)")
    void shouldRejectOverselling() {
        assertThatThrownBy(() -> inventory.reserve(11))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Insufficient stock");
    }

    @Test
    @DisplayName("Should correctly consume reservation upon payment confirmation")
    void shouldConsumeReservation() {
        inventory.reserve(5);
        inventory.consumeReservation(5);

        assertThat(inventory.getOnHand()).isEqualTo(5);
        assertThat(inventory.getReserved()).isEqualTo(0);
        assertThat(inventory.getAvailableQuantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("Should release reservation back to available pool upon order cancellation")
    void shouldReleaseReservation() {
        inventory.reserve(7);
        assertThat(inventory.getAvailableQuantity()).isEqualTo(3);

        inventory.releaseReservation(7);
        assertThat(inventory.getAvailableQuantity()).isEqualTo(10);
        assertThat(inventory.getReserved()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should reject negative stock initialisation")
    void shouldRejectNegativeInitialStock() {
        User user = new User(UUID.randomUUID(), "s@s.com", "h", "S", Role.SELLER);
        Seller seller = new Seller(UUID.randomUUID(), user, "S", "s@s.com", SellerStatus.ACTIVE);
        Product product = new Product(UUID.randomUUID(), seller, null, "I", "D", BigDecimal.ONE, "USD");

        assertThatThrownBy(() -> new Inventory(UUID.randomUUID(), "SKU-NEG", product, seller, -5))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Initial stock cannot be negative");
    }
}
