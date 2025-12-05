-- Migration V11: Add Client Salt for API Key Validation
-- Purpose: Enable API key validation for 100M+ users without login
-- 
-- Architecture:
-- - Each client gets a unique salt
-- - API key is hashed as: SHA-256(apiKey + clientSalt)
-- - Validation: SHA-256(providedKey + clientSalt) == storedHash
-- - Performance: ~1-5ms
--
-- Security:
-- - Unique salt per client prevents rainbow table attacks
-- - Long API keys (32+ chars) provide sufficient entropy
-- - Rate limiting protects against brute force
--
-- Migration:
-- - All existing clients will get a salt generated
-- - All clients will use SHA-256 validation (BCrypt no longer supported)

-- Enable pgcrypto extension for gen_random_bytes()
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Add client_salt column (unique salt per client)
ALTER TABLE client ADD COLUMN client_salt VARCHAR(64);

-- Generate unique salt for existing clients
-- Note: This is required for all clients as BCrypt is no longer supported
UPDATE client 
SET client_salt = encode(gen_random_bytes(32), 'hex')
WHERE client_salt IS NULL;

-- Make client_salt NOT NULL and UNIQUE
ALTER TABLE client ALTER COLUMN client_salt SET NOT NULL;
ALTER TABLE client ADD CONSTRAINT uk_client_salt UNIQUE (client_salt);

-- Create index for lookups
CREATE INDEX idx_client_salt ON client(client_salt);

-- Add comment
COMMENT ON COLUMN client.client_salt IS 'Unique salt per client for SHA-256 validation. Required for all clients.';

-- Important: After this migration, all clients must have a salt.
-- Clients without salt will fail validation.
-- The application now uses SHA-256 validation exclusively (BCrypt no longer supported).

