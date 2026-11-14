-- StockFlow High-Throughput Relational Query Indexes
-- Version: V4

-- 1. Accelerate customer and seller return requests lookup
CREATE INDEX IF NOT EXISTS idx_returns_customer ON return_requests(customer_id);
CREATE INDEX IF NOT EXISTS idx_returns_seller ON return_requests(seller_id);

-- 2. Accelerate multi-vendor seller shipments lookup
CREATE INDEX IF NOT EXISTS idx_shipments_seller ON shipments(seller_id);

-- 3. Accelerate seller inventory portfolio and low-stock queries
CREATE INDEX IF NOT EXISTS idx_inventory_seller ON inventory(seller_id);

-- 4. Accelerate time-series order sorting and customer dashboard queries
CREATE INDEX IF NOT EXISTS idx_orders_created_at ON orders(created_at DESC);
