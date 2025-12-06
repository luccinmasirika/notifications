ALTER TABLE client ADD COLUMN api_key_hash VARCHAR(255);

CREATE INDEX idx_client_api_key_hash ON client(api_key_hash);

COMMENT ON COLUMN client.api_key_hash IS 'BCrypt hash of the API key. Will eventually replace the plain text api_key column.';
