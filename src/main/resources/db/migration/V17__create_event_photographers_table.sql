-- Which photographers may upload to which events. A photographer with no row
-- for an event gets 403 on upload.
--
-- Events are soft-deleted (events.deleted_at), so the parent row survives and
-- no cascade is needed here.
CREATE TABLE event_photographers (
    event_id    UUID      NOT NULL REFERENCES events (id),
    user_id     UUID      NOT NULL REFERENCES users (id),
    assigned_at TIMESTAMP NOT NULL DEFAULT (now() AT TIME ZONE 'UTC'),
    PRIMARY KEY (event_id, user_id)
);

-- "Which events am I assigned to?" is the photographer's main query and filters
-- on user_id alone. The primary key leads with event_id, so it cannot serve it.
CREATE INDEX idx_event_photographers_user_id ON event_photographers (user_id);
