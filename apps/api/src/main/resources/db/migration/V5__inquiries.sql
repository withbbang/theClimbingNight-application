CREATE TABLE inquiries (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    title       VARCHAR(100) NOT NULL,
    body        TEXT         NOT NULL,
    status      VARCHAR(16)  NOT NULL,
    answer      TEXT         NULL,
    answered_by BIGINT       NULL,
    answered_at DATETIME(6)  NULL,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_inquiries_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_inquiries_answered_by FOREIGN KEY (answered_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_inquiries_user ON inquiries (user_id, id);
CREATE INDEX idx_inquiries_status ON inquiries (status, id);
