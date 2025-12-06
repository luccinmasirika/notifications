-- Migration V13: Force HMAC Authentication Only (Remove Legacy Support)
-- Purpose: Remove legacy authentication support, require HMAC for all clients
-- 
-- BREAKING CHANGE: All clients must now use HMAC authentication
-- Legacy clients will be unable to authenticate until they get an API secret
--
-- Steps:
-- 1. Update all clients to HMAC auth_method
-- 2. Remove LEGACY from auth_method constraint
-- 3. Update comments

-- Step 1: Update all clients to HMAC (definitive migration)
UPDATE client 
SET auth_method = 'HMAC' 
WHERE auth_method = 'LEGACY' OR auth_method IS NULL OR auth_method != 'HMAC';

-- Step 2: Remove LEGACY from constraint (only HMAC allowed)
ALTER TABLE client DROP CONSTRAINT IF EXISTS chk_client_auth_method;
ALTER TABLE client ADD CONSTRAINT chk_client_auth_method 
    CHECK (auth_method = 'HMAC');

-- Step 3: Update comments
COMMENT ON COLUMN client.api_secret_encrypted IS 'Encrypted API secret using AES-256-GCM. Format: base64(iv + encrypted_secret + auth_tag). REQUIRED for all clients. Clients without secrets cannot authenticate.';
COMMENT ON COLUMN client.auth_method IS 'Authentication method: HMAC only (legacy support removed). All clients must use HMAC-SHA256 authentication with X-API-KEY + X-TIMESTAMP + X-SIGNATURE headers.';

-- Important: 
-- - All clients now have auth_method='HMAC'
-- - Clients without api_secret_encrypted will fail authentication (401)
-- - Use AdminService.generateApiSecret() to generate secrets for existing clients
-- - All new clients are created with HMAC by default
-- - This is a DEFINITIVE migration - no rollback possible

