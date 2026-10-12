-- Per-user opt-outs for notification categories. A missing row means "enabled".
CREATE TABLE user_notification_preferences (
    id          VARCHAR(36)  PRIMARY KEY,
    user_id     VARCHAR(36)  NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category    VARCHAR(40)  NOT NULL,
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_user_notification_pref UNIQUE (user_id, category)
);

CREATE INDEX idx_user_notification_pref_user ON user_notification_preferences (user_id);
