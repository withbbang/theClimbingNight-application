CREATE TABLE notices (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    title        VARCHAR(100) NOT NULL,
    body         TEXT         NOT NULL,
    pinned       BOOLEAN      NOT NULL DEFAULT FALSE,
    author_id    BIGINT       NOT NULL,
    published_at DATETIME(6)  NOT NULL,
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_notices_author FOREIGN KEY (author_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_notices_list ON notices (pinned, published_at, id);

CREATE TABLE app_settings (
    setting_key   VARCHAR(50)  NOT NULL,
    setting_value VARCHAR(500) NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (setting_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
