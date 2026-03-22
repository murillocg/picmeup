CREATE TABLE order_items (
    id       UUID    PRIMARY KEY,
    order_id UUID    NOT NULL REFERENCES orders (id),
    photo_id UUID    NOT NULL REFERENCES photos (id),
    price    INTEGER NOT NULL
);

CREATE INDEX idx_order_items_order_id ON order_items (order_id);
