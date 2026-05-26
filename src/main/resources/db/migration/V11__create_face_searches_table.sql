CREATE TABLE face_searches (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL REFERENCES events(id),
    results_count INT NOT NULL,
    searched_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_face_searches_event_id ON face_searches(event_id);
CREATE INDEX idx_face_searches_searched_at ON face_searches(searched_at);
