CREATE TABLE posts (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    author_id     BIGINT        NOT NULL,
    body          VARCHAR(2200) NOT NULL,
    status        VARCHAR(16)   NOT NULL,
    like_count    INT           NOT NULL DEFAULT 0,
    comment_count INT           NOT NULL DEFAULT 0,
    published_at  DATETIME(6)   NULL,
    deleted_at    DATETIME(6)   NULL,
    created_at    DATETIME(6)   NOT NULL,
    updated_at    DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_posts_author FOREIGN KEY (author_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_posts_feed ON posts (status, published_at, id);
CREATE INDEX idx_posts_author ON posts (author_id);

CREATE TABLE post_media (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    post_id       BIGINT       NOT NULL,
    type          VARCHAR(16)  NOT NULL,
    status        VARCHAR(16)  NOT NULL,
    sort_order    INT          NOT NULL,
    upload_key    VARCHAR(128) NOT NULL,
    original_path VARCHAR(128) NULL,
    public_key    VARCHAR(128) NULL,
    width         INT          NULL,
    height        INT          NULL,
    byte_size     BIGINT       NULL,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_post_media_post FOREIGN KEY (post_id) REFERENCES posts (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_post_media_post ON post_media (post_id, sort_order);

CREATE TABLE media_jobs (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    kind        VARCHAR(24)  NOT NULL,
    priority    INT          NOT NULL,
    target_id   BIGINT       NOT NULL,
    status      VARCHAR(16)  NOT NULL,
    attempts    INT          NOT NULL DEFAULT 0,
    next_run_at DATETIME(6)  NOT NULL,
    locked_at   DATETIME(6)  NULL,
    last_error  VARCHAR(500) NULL,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_media_jobs_claim ON media_jobs (status, priority, next_run_at);
CREATE INDEX idx_media_jobs_target ON media_jobs (target_id, kind);

CREATE TABLE reports (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    reporter_id BIGINT       NOT NULL,
    target_type VARCHAR(16)  NOT NULL,
    target_id   BIGINT       NOT NULL,
    reason      VARCHAR(24)  NOT NULL,
    detail      VARCHAR(500) NULL,
    status      VARCHAR(16)  NOT NULL,
    handled_by  BIGINT       NULL,
    handled_at  DATETIME(6)  NULL,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_reports_reporter FOREIGN KEY (reporter_id) REFERENCES users (id),
    CONSTRAINT fk_reports_handled_by FOREIGN KEY (handled_by) REFERENCES users (id),
    CONSTRAINT uk_reports_reporter_target UNIQUE (reporter_id, target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_reports_target ON reports (target_type, target_id);

CREATE TABLE user_blocks (
    blocker_id BIGINT      NOT NULL,
    blocked_id BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (blocker_id, blocked_id),
    CONSTRAINT fk_user_blocks_blocker FOREIGN KEY (blocker_id) REFERENCES users (id),
    CONSTRAINT fk_user_blocks_blocked FOREIGN KEY (blocked_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_user_blocks_blocked ON user_blocks (blocked_id);
