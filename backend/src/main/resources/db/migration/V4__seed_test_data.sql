INSERT INTO client (api_key, name, active, created_at)
VALUES ('test-api-key-123', 'Test Client', true, CURRENT_TIMESTAMP);

INSERT INTO client_limit (client_id, window_size_seconds, max_requests_per_window, monthly_quota, created_at)
VALUES (
    (SELECT id FROM client WHERE api_key = 'test-api-key-123'),
    10,
    100,
    10000,
    CURRENT_TIMESTAMP
);

INSERT INTO client (api_key, name, active, created_at)
VALUES ('demo-key-456', 'Demo Client', true, CURRENT_TIMESTAMP);

INSERT INTO client_limit (client_id, window_size_seconds, max_requests_per_window, monthly_quota, created_at)
VALUES (
    (SELECT id FROM client WHERE api_key = 'demo-key-456'),
    60,
    50,
    50000,
    CURRENT_TIMESTAMP
);
