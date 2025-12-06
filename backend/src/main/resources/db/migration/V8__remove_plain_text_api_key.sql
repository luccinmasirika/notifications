DO $$
DECLARE
    clients_without_hash INTEGER;
    clients_deleted INTEGER;
BEGIN
    SELECT COUNT(*) INTO clients_without_hash
    FROM client
    WHERE api_key_hash IS NULL;

    IF clients_without_hash > 0 THEN
        DELETE FROM client WHERE api_key_hash IS NULL;
        GET DIAGNOSTICS clients_deleted = ROW_COUNT;
        
        RAISE NOTICE 'V8 Migration: Removed % clients without api_key_hash (these clients could not be authenticated)', clients_deleted;
    END IF;
END $$;

ALTER TABLE client ALTER COLUMN api_key_hash SET NOT NULL;
ALTER TABLE client ADD CONSTRAINT uk_client_api_key_hash UNIQUE (api_key_hash);

ALTER TABLE client DROP COLUMN api_key;

COMMENT ON COLUMN client.api_key_hash IS 'BCrypt hash of the API key (primary authentication method)';
