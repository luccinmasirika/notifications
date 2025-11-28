-- Add priority column to client table
ALTER TABLE client
ADD COLUMN priority INTEGER DEFAULT 0;

-- Create index on priority for potential future queries
CREATE INDEX idx_client_priority ON client(priority);

-- Update existing clients to have default priority
UPDATE client SET priority = 0 WHERE priority IS NULL;
