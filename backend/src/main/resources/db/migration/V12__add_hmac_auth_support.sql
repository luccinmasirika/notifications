-- Migration V12: Add HMAC-SHA256 Authentication Support
-- Purpose: Enable secure HMAC-based authentication with API Key + Secret
-- 
-- Architecture:
-- - API Key: Public identifier (stored as BCrypt hash)
-- - API Secret: Private key (encrypted with AES-256-GCM)
-- - Signature: HMAC-SHA256(timestamp + method + path + body)
-- - Headers: X-API-KEY, X-TIMESTAMP, X-SIGNATURE
--
-- Security:
-- - API Secret never transmitted (only signature)
-- - Timestamp validation prevents replay attacks
-- - Constant-time comparison prevents timing attacks
-- - AES-256-GCM encryption for secret storage
--
-- Note: Legacy support was removed in V13. All clients must use HMAC.

-- Add api_secret_encrypted column (encrypted with AES-256-GCM)
ALTER TABLE client ADD COLUMN api_secret_encrypted TEXT;

-- Add auth_method column (HMAC only - legacy removed in V13)
ALTER TABLE client ADD COLUMN auth_method VARCHAR(20) DEFAULT 'HMAC';

-- Add status column (ACTIVE, SUSPENDED, REVOKED)
ALTER TABLE client ADD COLUMN status VARCHAR(20) DEFAULT 'ACTIVE';

-- Create index on auth_method for filtering
CREATE INDEX idx_client_auth_method ON client(auth_method) WHERE auth_method IS NOT NULL;

-- Create index on status for filtering
CREATE INDEX idx_client_status ON client(status) WHERE status IS NOT NULL;

-- Add comments
COMMENT ON COLUMN client.api_secret_encrypted IS 'Encrypted API secret using AES-256-GCM. Format: base64(iv + encrypted_secret + auth_tag). NULL for legacy clients.';
COMMENT ON COLUMN client.auth_method IS 'Authentication method: LEGACY (X-API-KEY only) or HMAC (X-API-KEY + X-SIGNATURE). Default: LEGACY.';
COMMENT ON COLUMN client.status IS 'Client status: ACTIVE, SUSPENDED, or REVOKED. Default: ACTIVE.';

-- Update existing clients to HMAC mode (legacy removed)
UPDATE client SET auth_method = 'HMAC' WHERE auth_method IS NULL;
UPDATE client SET status = 'ACTIVE' WHERE status IS NULL;

-- Make auth_method and status NOT NULL
ALTER TABLE client ALTER COLUMN auth_method SET NOT NULL;
ALTER TABLE client ALTER COLUMN status SET NOT NULL;

-- Add constraint for valid auth_method values (HMAC only)
ALTER TABLE client ADD CONSTRAINT chk_client_auth_method 
    CHECK (auth_method = 'HMAC');

-- Add constraint for valid status values
ALTER TABLE client ADD CONSTRAINT chk_client_status 
    CHECK (status IN ('ACTIVE', 'SUSPENDED', 'REVOKED'));

-- Important: 
-- - All clients must use HMAC authentication (X-API-KEY + X-TIMESTAMP + X-SIGNATURE)
-- - api_secret_encrypted is REQUIRED for all clients
-- - Legacy support removed in V13

