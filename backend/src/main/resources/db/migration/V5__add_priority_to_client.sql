ALTER TABLE client
ADD COLUMN priority INTEGER DEFAULT 0;

CREATE INDEX idx_client_priority ON client(priority);

UPDATE client SET priority = 0 WHERE priority IS NULL;
