-- Revenue is reported per event, but an order could only reach its event through
-- order_items -> photos -> events. That path is awkward and lossy: an item's price is
-- derived by dividing the order total across the photos, so summing items drifts from
-- what was actually charged whenever the pack cap rounds ($65 over 3 photos sums to
-- $65.01).
--
-- Orders were already single-event in practice -- createOrder prices the whole cart
-- from the first photo's event -- so this stores an assumption the code was already
-- making. OrderService now enforces it rather than leaving it implicit.
ALTER TABLE orders ADD COLUMN event_id UUID REFERENCES events (id);

UPDATE orders o
SET event_id = (
    SELECT p.event_id
    FROM order_items oi
             JOIN photos p ON p.id = oi.photo_id
    WHERE oi.order_id = o.id
    LIMIT 1
);

-- Left nullable on purpose: an order whose photos were hard-deleted has nothing to
-- backfill from, and failing this migration over historical rows helps nobody. The
-- revenue queries skip those rows rather than guessing at an event for them.
CREATE INDEX idx_orders_event_id ON orders (event_id);
