UPDATE client 
SET auth_method = 'HMAC' 
WHERE auth_method = 'LEGACY' OR auth_method IS NULL OR auth_method != 'HMAC';

ALTER TABLE client DROP CONSTRAINT IF EXISTS chk_client_auth_method;
ALTER TABLE client ADD CONSTRAINT chk_client_auth_method 
    CHECK (auth_method = 'HMAC');

COMMENT ON COLUMN client.api_secret_encrypted IS 'Encrypted API secret using AES-256-GCM. Format: base64(iv + encrypted_secret + auth_tag). REQUIRED for all clients. Clients without secrets cannot authenticate.';
COMMENT ON COLUMN client.auth_method IS 'Authentication method: HMAC only (legacy support removed). All clients must use HMAC-SHA256 authentication with X-API-KEY + X-TIMESTAMP + X-SIGNATURE headers.';

