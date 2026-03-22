CREATE TABLE photographers (
    id    UUID PRIMARY KEY,
    name  VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE
);

CREATE INDEX idx_photographers_email ON photographers (email);
