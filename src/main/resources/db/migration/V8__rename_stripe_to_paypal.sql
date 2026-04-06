ALTER TABLE orders RENAME COLUMN stripe_session_id TO paypal_order_id;

DROP INDEX idx_orders_stripe_session_id;
CREATE INDEX idx_orders_paypal_order_id ON orders (paypal_order_id);
