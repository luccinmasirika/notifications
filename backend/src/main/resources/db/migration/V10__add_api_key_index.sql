-- Migration V10: Add API Key Index for Fast Lookup
-- Purpose: Add SHA-256 index to enable O(1) lookup instead of O(n) BCrypt verification loop
-- This dramatically improves API key validation performance from O(n*200ms) to O(1) + 1*200ms

-- Add new column for SHA-256 index (fast, deterministic hash for lookup)
-- Column is nullable to support existing clients during migration period
ALTER TABLE client ADD COLUMN api_key_index VARCHAR(64);

-- Create unique index on the SHA-256 index for O(1) lookups
-- Using partial index to allow NULL values (PostgreSQL supports this)
CREATE UNIQUE INDEX idx_client_api_key_index ON client(api_key_index) WHERE api_key_index IS NOT NULL;

-- Add comment explaining the column
COMMENT ON COLUMN client.api_key_index IS 'SHA-256 hash of the API key. Used for fast O(1) lookup before BCrypt verification. NULL for legacy clients without index.';

-- Note: This index is deterministic (same input = same output) unlike BCrypt which uses random salt
-- This allows direct database lookup, then we verify with BCrypt only for the matching client
-- 
-- Migration strategy:
-- - New clients will have api_key_index populated automatically
-- - Existing clients will have NULL api_key_index and use fallback O(n) validation
-- - When existing clients update their API key, the index will be populated
-- - System is backward compatible during migration period

