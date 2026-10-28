-- Seed data for local testing
-- Admin: admin@stockflow.com / Password123!
-- Seller: seller@stockflow.com / Password123!
-- Customer: customer@stockflow.com / Password123!
-- (BCrypt hashes for 'Password123!')

INSERT INTO users (id, email, password_hash, full_name, role, enabled, created_at, updated_at) VALUES
('a0000000-0000-0000-0000-000000000001', 'admin@stockflow.com', '$2a$10$7EqJtq98hPqEX7fNZaFWoO9v916a6Kov76QpW84d3.3j16f3d53m6', 'System Administrator', 'ADMIN', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('a0000000-0000-0000-0000-000000000002', 'seller@stockflow.com', '$2a$10$7EqJtq98hPqEX7fNZaFWoO9v916a6Kov76QpW84d3.3j16f3d53m6', 'Apex Retail Store', 'SELLER', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('a0000000-0000-0000-0000-000000000003', 'customer@stockflow.com', '$2a$10$7EqJtq98hPqEX7fNZaFWoO9v916a6Kov76QpW84d3.3j16f3d53m6', 'Jane Customer', 'CUSTOMER', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO sellers (id, user_id, store_name, business_email, status, created_at, updated_at) VALUES
('b0000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000002', 'Apex Electronics', 'seller@stockflow.com', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO categories (id, name, slug, description, parent_id, active, created_at) VALUES
('c0000000-0000-0000-0000-000000000001', 'Electronics', 'electronics', 'Consumer Electronics and Gadgets', NULL, true, CURRENT_TIMESTAMP),
('c0000000-0000-0000-0000-000000000002', 'Audio', 'audio', 'Headphones, Speakers and Audio gear', 'c0000000-0000-0000-0000-000000000001', true, CURRENT_TIMESTAMP);

INSERT INTO products (id, seller_id, category_id, title, description, price, currency, active, created_at, updated_at) VALUES
('d0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000002', 'Noise Cancelling Headphones Pro', 'Premium wireless ANC headphones', 199.99, 'USD', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO inventory (id, sku, product_id, seller_id, on_hand, reserved, version, updated_at) VALUES
('e0000000-0000-0000-0000-000000000001', 'SKU-AUDIO-PRO-BLK', 'd0000000-0000-0000-0000-000000000001', 'b0000000-0000-0000-0000-000000000001', 50, 0, 0, CURRENT_TIMESTAMP);

INSERT INTO inventory_movements (id, sku, quantity_change, previous_on_hand, new_on_hand, reason_code, reference_id, performed_by, created_at) VALUES
('f0000000-0000-0000-0000-000000000001', 'SKU-AUDIO-PRO-BLK', 50, 0, 50, 'INITIAL_STOCK', 'INIT-001', 'SYSTEM', CURRENT_TIMESTAMP);
