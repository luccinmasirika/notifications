ALTER TABLE client ADD COLUMN api_key_index VARCHAR(64);

CREATE UNIQUE INDEX idx_client_api_key_index ON client(api_key_index) WHERE api_key_index IS NOT NULL;

COMMENT ON COLUMN client.api_key_index IS 'SHA-256 hash of the API key. Used for fast O(1) lookup before BCrypt verification. NULL for legacy clients without index.';

