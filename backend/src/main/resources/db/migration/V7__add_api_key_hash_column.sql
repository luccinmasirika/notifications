-- Migration V7: Add API Key Hash Column
-- Purpose: Add support for hashed API keys (BCrypt) for better security
-- This migration is backward compatible - the plain text api_key column is kept temporarily

-- Add new column for hashed API keys
ALTER TABLE client ADD COLUMN api_key_hash VARCHAR(255);

-- Create index on the hash column for efficient lookups
CREATE INDEX idx_client_api_key_hash ON client(api_key_hash);

-- Add comment explaining the column
COMMENT ON COLUMN client.api_key_hash IS 'BCrypt hash of the API key. Will eventually replace the plain text api_key column.';

-- Note: The api_key column is kept for backward compatibility during migration
-- It will be removed in a future version (V8) after all API keys have been migrated
