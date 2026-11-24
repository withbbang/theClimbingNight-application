CREATE TABLE push_tokens (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    expo_push_token VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    platform        VARCHAR(16)  NOT NULL,
    last_seen_at    DATETIME(6)  NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_push_tokens_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_push_tokens_token UNIQUE (expo_push_token)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_push_tokens_user ON push_tokens (user_id);

CREATE TABLE notification_settings (
    user_id    BIGINT      NOT NULL,
    new_meetup BOOLEAN     NOT NULL,
    new_notice BOOLEAN     NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_notification_settings_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE push_tickets (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    ticket_id       VARCHAR(64) NOT NULL,
    expo_push_token VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    sent_at         DATETIME(6) NOT NULL,
    created_at      DATETIME(6) NOT NULL,
    updated_at      DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_push_tickets_sent ON push_tickets (sent_at, id);

ALTER TABLE meetups ADD COLUMN reminder_sent_at DATETIME(6) NULL;
