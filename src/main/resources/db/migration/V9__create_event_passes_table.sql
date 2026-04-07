CREATE TABLE event_passes (
    id               UUID PRIMARY KEY,
    event_id         UUID         NOT NULL REFERENCES events(id),
    buyer_email      VARCHAR(255) NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    paypal_order_id  VARCHAR(255),
    price            DECIMAL(10,2) NOT NULL,
    currency         VARCHAR(3)   NOT NULL DEFAULT 'AUD',
    created_at       TIMESTAMP    NOT NULL DEFAULT (now() AT TIME ZONE 'UTC'),
    redeemed_at      TIMESTAMP
);

CREATE INDEX idx_event_passes_event_id ON event_passes (event_id);
CREATE INDEX idx_event_passes_buyer_email ON event_passes (buyer_email);
CREATE INDEX idx_event_passes_status ON event_passes (status);
