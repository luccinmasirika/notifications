CREATE TABLE client_limit (
    id BIGSERIAL PRIMARY KEY,
    client_id BIGINT NOT NULL REFERENCES client(id) ON DELETE CASCADE,
    window_size_seconds INT NOT NULL,
    max_requests_per_window INT NOT NULL,
    monthly_quota INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(client_id)
);

CREATE INDEX idx_client_limit_client_id ON client_limit(client_id);
