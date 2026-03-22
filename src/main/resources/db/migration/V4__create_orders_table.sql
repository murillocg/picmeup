CREATE TABLE orders (
    id                 UUID PRIMARY KEY,
    buyer_email        VARCHAR(255) NOT NULL,
    stripe_session_id  VARCHAR(255),
    status             VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    total_amount       INTEGER      NOT NULL,
    currency           VARCHAR(3)   NOT NULL DEFAULT 'AUD',
    created_at         TIMESTAMP NOT NULL DEFAULT (now() AT TIME ZONE 'UTC')
);

CREATE INDEX idx_orders_stripe_session_id ON orders (stripe_session_id);
CREATE INDEX idx_orders_status ON orders (status);
