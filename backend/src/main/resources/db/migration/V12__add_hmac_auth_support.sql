ALTER TABLE client ADD COLUMN api_secret_encrypted TEXT;

ALTER TABLE client ADD COLUMN auth_method VARCHAR(20) DEFAULT 'HMAC';

ALTER TABLE client ADD COLUMN status VARCHAR(20) DEFAULT 'ACTIVE';

CREATE INDEX idx_client_auth_method ON client(auth_method) WHERE auth_method IS NOT NULL;

CREATE INDEX idx_client_status ON client(status) WHERE status IS NOT NULL;

COMMENT ON COLUMN client.api_secret_encrypted IS 'Encrypted API secret using AES-256-GCM. Format: base64(iv + encrypted_secret + auth_tag). NULL for legacy clients.';
COMMENT ON COLUMN client.auth_method IS 'Authentication method: LEGACY (X-API-KEY only) or HMAC (X-API-KEY + X-SIGNATURE). Default: LEGACY.';
COMMENT ON COLUMN client.status IS 'Client status: ACTIVE, SUSPENDED, or REVOKED. Default: ACTIVE.';

UPDATE client SET auth_method = 'HMAC' WHERE auth_method IS NULL;
UPDATE client SET status = 'ACTIVE' WHERE status IS NULL;

ALTER TABLE client ALTER COLUMN auth_method SET NOT NULL;
ALTER TABLE client ALTER COLUMN status SET NOT NULL;

ALTER TABLE client ADD CONSTRAINT chk_client_auth_method 
    CHECK (auth_method = 'HMAC');

ALTER TABLE client ADD CONSTRAINT chk_client_status 
    CHECK (status IN ('ACTIVE', 'SUSPENDED', 'REVOKED'));

