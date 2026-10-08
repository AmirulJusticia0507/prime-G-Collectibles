CREATE INDEX idx_orders_fulfillment_status ON orders (fulfillment_status);
CREATE INDEX idx_payments_status_expired_at ON payments (payment_status, expired_at);
CREATE INDEX idx_order_items_order_id ON order_items (order_id);
