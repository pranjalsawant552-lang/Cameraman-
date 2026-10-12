-- ============================================================
-- V7__broadcast_campaigns.sql
-- Bulk WhatsApp messaging module: design a message (text + optional
-- image/video) and send it to every client with a mobile number on file.
-- ============================================================

CREATE TABLE broadcast_campaigns (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    title VARCHAR(150) NOT NULL,
    message_text VARCHAR(4000) NOT NULL,
    media_url VARCHAR(1000),
    media_type VARCHAR(10),                 -- NONE, IMAGE, VIDEO
    status VARCHAR(20) NOT NULL DEFAULT 'QUEUED', -- QUEUED, SENDING, COMPLETED, FAILED
    total_recipients INT NOT NULL DEFAULT 0,
    sent_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    created_by VARCHAR(36) REFERENCES users(id),
    created_by_name VARCHAR(150),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    started_at TIMESTAMP,
    completed_at TIMESTAMP
);

CREATE TABLE broadcast_recipients (
    id VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    campaign_id VARCHAR(36) NOT NULL REFERENCES broadcast_campaigns(id) ON DELETE CASCADE,
    user_id VARCHAR(36) REFERENCES users(id),
    name VARCHAR(150),
    mobile VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING, SENT, FAILED
    error_message VARCHAR(500),
    sent_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_broadcast_recipients_campaign ON broadcast_recipients(campaign_id);
CREATE INDEX idx_broadcast_recipients_status ON broadcast_recipients(campaign_id, status);
CREATE INDEX idx_broadcast_campaigns_created ON broadcast_campaigns(created_at DESC);
