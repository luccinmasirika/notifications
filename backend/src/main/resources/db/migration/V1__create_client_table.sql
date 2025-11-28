CREATE TABLE client (
    id BIGSERIAL PRIMARY KEY,
    api_key VARCHAR(255) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_client_api_key ON client(api_key);
CREATE INDEX idx_client_active ON client(active);
