-- StockFlow Core Relational Schema
-- Version: V1

CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(150) NOT NULL,
    role VARCHAR(30) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_role ON users(role);

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);

CREATE TABLE sellers (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE RESTRICT,
    store_name VARCHAR(150) NOT NULL UNIQUE,
    business_email VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE categories (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    slug VARCHAR(120) NOT NULL UNIQUE,
    description VARCHAR(500),
    parent_id UUID REFERENCES categories(id) ON DELETE SET NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE products (
    id UUID PRIMARY KEY,
    seller_id UUID NOT NULL REFERENCES sellers(id) ON DELETE RESTRICT,
    category_id UUID REFERENCES categories(id) ON DELETE RESTRICT,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    price NUMERIC(15, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_product_price CHECK (price >= 0)
);
CREATE INDEX idx_products_seller ON products(seller_id);
CREATE INDEX idx_products_category ON products(category_id);
CREATE INDEX idx_products_active ON products(active);

CREATE TABLE inventory (
    id UUID PRIMARY KEY,
    sku VARCHAR(100) NOT NULL UNIQUE,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    seller_id UUID NOT NULL REFERENCES sellers(id) ON DELETE RESTRICT,
    on_hand INT NOT NULL DEFAULT 0,
    reserved INT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_inventory_on_hand CHECK (on_hand >= 0),
    CONSTRAINT chk_inventory_reserved CHECK (reserved >= 0),
    CONSTRAINT chk_inventory_available CHECK (reserved <= on_hand)
);
CREATE INDEX idx_inventory_sku ON inventory(sku);
CREATE INDEX idx_inventory_product ON inventory(product_id);

CREATE TABLE inventory_movements (
    id UUID PRIMARY KEY,
    sku VARCHAR(100) NOT NULL,
    quantity_change INT NOT NULL,
    previous_on_hand INT NOT NULL,
    new_on_hand INT NOT NULL,
    reason_code VARCHAR(50) NOT NULL,
    reference_id VARCHAR(100),
    performed_by VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_movements_sku ON inventory_movements(sku);

CREATE TABLE stock_reservations (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    sku VARCHAR(100) NOT NULL,
    quantity INT NOT NULL,
    status VARCHAR(30) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_reservation_qty CHECK (quantity > 0)
);
CREATE INDEX idx_reservations_order ON stock_reservations(order_id);
CREATE INDEX idx_reservations_status_expires ON stock_reservations(status, expires_at);

CREATE TABLE carts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE cart_items (
    id UUID PRIMARY KEY,
    cart_id UUID NOT NULL REFERENCES carts(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    sku VARCHAR(100) NOT NULL,
    quantity INT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_cart_sku UNIQUE (cart_id, sku),
    CONSTRAINT chk_cart_item_qty CHECK (quantity > 0)
);

CREATE TABLE orders (
    id UUID PRIMARY KEY,
    order_number VARCHAR(50) NOT NULL UNIQUE,
    customer_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    status VARCHAR(30) NOT NULL,
    total_amount NUMERIC(15, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    shipping_address TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_order_total CHECK (total_amount >= 0)
);
CREATE INDEX idx_orders_customer ON orders(customer_id);
CREATE INDEX idx_orders_status ON orders(status);

CREATE TABLE order_items (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    seller_id UUID NOT NULL REFERENCES sellers(id) ON DELETE RESTRICT,
    product_id UUID NOT NULL,
    sku VARCHAR(100) NOT NULL,
    product_title VARCHAR(255) NOT NULL,
    unit_price NUMERIC(15, 2) NOT NULL,
    quantity INT NOT NULL,
    discount_amount NUMERIC(15, 2) NOT NULL DEFAULT 0.00,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    CONSTRAINT chk_order_item_qty CHECK (quantity > 0),
    CONSTRAINT chk_order_item_price CHECK (unit_price >= 0)
);
CREATE INDEX idx_order_items_order ON order_items(order_id);

CREATE TABLE idempotency_records (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(128) NOT NULL,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    request_hash VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    response_status_code INT,
    response_body TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_idempotency_user_key UNIQUE (idempotency_key, user_id)
);
CREATE INDEX idx_idempotency_lookup ON idempotency_records(idempotency_key, user_id);

CREATE TABLE payments (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE RESTRICT,
    provider_name VARCHAR(50) NOT NULL,
    provider_reference VARCHAR(100),
    amount NUMERIC(15, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    status VARCHAR(30) NOT NULL,
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_payments_order ON payments(order_id);
CREATE INDEX idx_payments_status ON payments(status);

CREATE TABLE payment_webhook_events (
    id UUID PRIMARY KEY,
    event_id VARCHAR(100) NOT NULL UNIQUE,
    provider_name VARCHAR(50) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    payload TEXT NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE refunds (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE RESTRICT,
    payment_id UUID NOT NULL REFERENCES payments(id) ON DELETE RESTRICT,
    amount NUMERIC(15, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    reason VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL,
    provider_refund_id VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_refund_amount CHECK (amount > 0)
);

CREATE TABLE shipments (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE RESTRICT,
    seller_id UUID NOT NULL REFERENCES sellers(id) ON DELETE RESTRICT,
    carrier VARCHAR(50) NOT NULL,
    tracking_number VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL,
    shipped_at TIMESTAMP WITH TIME ZONE,
    delivered_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_shipments_order ON shipments(order_id);

CREATE TABLE return_requests (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE RESTRICT,
    customer_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    seller_id UUID NOT NULL REFERENCES sellers(id) ON DELETE RESTRICT,
    reason TEXT NOT NULL,
    status VARCHAR(30) NOT NULL,
    restock_decision VARCHAR(30) NOT NULL DEFAULT 'NONE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_returns_order ON return_requests(order_id);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_outbox_pending ON outbox_events(status, next_attempt_at);

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,
    user_id UUID,
    action VARCHAR(100) NOT NULL,
    resource_type VARCHAR(50) NOT NULL,
    resource_id VARCHAR(100) NOT NULL,
    details TEXT,
    ip_address VARCHAR(50),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_audit_resource ON audit_logs(resource_type, resource_id);
