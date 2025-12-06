CREATE TABLE system_limit (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) UNIQUE NOT NULL,
    window_size_seconds INT NOT NULL,
    max_requests_per_window INT NOT NULL,
    active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_system_limit_name ON system_limit(name);
CREATE INDEX idx_system_limit_active ON system_limit(active);

INSERT INTO system_limit (name, window_size_seconds, max_requests_per_window, active)
VALUES ('global_rate_limit', 10, 10000, true);
