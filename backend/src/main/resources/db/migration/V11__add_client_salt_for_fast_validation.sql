CREATE EXTENSION IF NOT EXISTS pgcrypto;

ALTER TABLE client ADD COLUMN client_salt VARCHAR(64);

UPDATE client 
SET client_salt = encode(gen_random_bytes(32), 'hex')
WHERE client_salt IS NULL;

ALTER TABLE client ALTER COLUMN client_salt SET NOT NULL;
ALTER TABLE client ADD CONSTRAINT uk_client_salt UNIQUE (client_salt);

CREATE INDEX idx_client_salt ON client(client_salt);

COMMENT ON COLUMN client.client_salt IS 'Unique salt per client for SHA-256 validation. Required for all clients.';

