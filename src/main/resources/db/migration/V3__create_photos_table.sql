CREATE TABLE photos (
    id                    UUID PRIMARY KEY,
    event_id              UUID         NOT NULL REFERENCES events (id),
    photographer_id       UUID         NOT NULL REFERENCES photographers (id),
    original_s3_key       VARCHAR(512),
    thumbnail_s3_key      VARCHAR(512),
    rekognition_face_ids  TEXT[],
    uploaded_at           TIMESTAMP NOT NULL DEFAULT (now() AT TIME ZONE 'UTC'),
    status                VARCHAR(20)  NOT NULL DEFAULT 'PROCESSING'
);

CREATE INDEX idx_photos_event_id ON photos (event_id);
CREATE INDEX idx_photos_photographer_id ON photos (photographer_id);
CREATE INDEX idx_photos_status ON photos (status);
