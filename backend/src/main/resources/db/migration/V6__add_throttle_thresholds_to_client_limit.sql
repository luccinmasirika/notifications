ALTER TABLE client_limit
ADD COLUMN soft_throttle_threshold DOUBLE PRECISION NOT NULL DEFAULT 0.80,
ADD COLUMN hard_reject_threshold DOUBLE PRECISION NOT NULL DEFAULT 1.00;

COMMENT ON COLUMN client_limit.soft_throttle_threshold IS 'Threshold (0.0-1.0) at which soft throttling begins. Default: 0.80 (80%)';
COMMENT ON COLUMN client_limit.hard_reject_threshold IS 'Threshold (0.0-1.0) at which requests are hard rejected. Default: 1.00 (100%)';

UPDATE client_limit
SET soft_throttle_threshold = 0.80,
    hard_reject_threshold = 1.00
WHERE soft_throttle_threshold IS NULL OR hard_reject_threshold IS NULL;
