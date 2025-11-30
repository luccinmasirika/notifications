-- Migration V8: Remove Plain Text API Key Column
-- Purpose: Complete the migration to hashed API keys by removing the insecure plain text column
-- WARNING: This is a breaking change. Clients without api_key_hash will be removed.

-- Step 1: Clean up clients without api_key_hash
-- These clients cannot be authenticated anyway, so we remove them
DO $$
DECLARE
    clients_without_hash INTEGER;
    clients_deleted INTEGER;
BEGIN
    -- Count clients without hash
    SELECT COUNT(*) INTO clients_without_hash
    FROM client
    WHERE api_key_hash IS NULL;

    -- Delete clients without hash (they cannot be authenticated)
    IF clients_without_hash > 0 THEN
        DELETE FROM client WHERE api_key_hash IS NULL;
        GET DIAGNOSTICS clients_deleted = ROW_COUNT;
        
        -- Log the cleanup (using RAISE NOTICE for visibility)
        RAISE NOTICE 'V8 Migration: Removed % clients without api_key_hash (these clients could not be authenticated)', clients_deleted;
    END IF;
END $$;

-- Step 2: Make api_key_hash NOT NULL and UNIQUE
ALTER TABLE client ALTER COLUMN api_key_hash SET NOT NULL;
ALTER TABLE client ADD CONSTRAINT uk_client_api_key_hash UNIQUE (api_key_hash);

-- Step 3: Drop the old plain text api_key column
ALTER TABLE client DROP COLUMN api_key;

-- Step 4: Add comment
COMMENT ON COLUMN client.api_key_hash IS 'BCrypt hash of the API key (primary authentication method)';

-- Migration complete - system now uses only hashed API keys
