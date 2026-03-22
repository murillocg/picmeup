CREATE TABLE events (
    id         UUID PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    date       DATE         NOT NULL,
    location   VARCHAR(255) NOT NULL,
    slug       VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT (now() AT TIME ZONE 'UTC'),
    expires_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_events_slug ON events (slug);
CREATE INDEX idx_events_expires_at ON events (expires_at);
