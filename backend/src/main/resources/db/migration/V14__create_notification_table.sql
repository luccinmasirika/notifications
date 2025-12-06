-- Create notification table for tracking notification status
CREATE TABLE notification (
    id BIGSERIAL PRIMARY KEY,
    client_id BIGINT NOT NULL,
    client_name VARCHAR(255) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    recipient VARCHAR(255) NOT NULL,
    message VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP NULL,
    error_message VARCHAR(1000) NULL
);

-- Create indexes for better query performance
CREATE INDEX idx_notification_client_id ON notification(client_id);
CREATE INDEX idx_notification_status ON notification(status);
CREATE INDEX idx_notification_created_at ON notification(created_at);
CREATE INDEX idx_notification_client_status ON notification(client_id, status);

-- Add comment to table
COMMENT ON TABLE notification IS 'Stores notification records with their processing status';
COMMENT ON COLUMN notification.status IS 'Status: PENDING, PROCESSING, SENT, FAILED';
