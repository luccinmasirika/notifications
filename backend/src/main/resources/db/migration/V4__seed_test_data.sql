-- Insert test client
INSERT INTO client (api_key, name, active, created_at)
VALUES ('test-api-key-123', 'Test Client', true, CURRENT_TIMESTAMP);

-- Get the client ID (will be 1 in a fresh database)
-- Insert client rate limit configuration
INSERT INTO client_limit (client_id, window_size_seconds, max_requests_per_window, monthly_quota, created_at)
VALUES (
    (SELECT id FROM client WHERE api_key = 'test-api-key-123'),
    10,     -- 10 second window
    100,    -- 100 requests per 10 seconds
    10000,  -- 10,000 requests per month
    CURRENT_TIMESTAMP
);

-- Insert another test client for demo purposes
INSERT INTO client (api_key, name, active, created_at)
VALUES ('demo-key-456', 'Demo Client', true, CURRENT_TIMESTAMP);

INSERT INTO client_limit (client_id, window_size_seconds, max_requests_per_window, monthly_quota, created_at)
VALUES (
    (SELECT id FROM client WHERE api_key = 'demo-key-456'),
    60,     -- 60 second window
    50,     -- 50 requests per minute
    50000,  -- 50,000 requests per month
    CURRENT_TIMESTAMP
);
