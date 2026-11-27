CREATE TABLE comments (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    post_id           BIGINT       NOT NULL,
    author_id         BIGINT       NOT NULL,
    parent_id         BIGINT       NULL,
    reply_to_user_id  BIGINT       NULL,
    body              VARCHAR(500) NOT NULL,
    status            VARCHAR(16)  NOT NULL,
    deleted_at        DATETIME(6)  NULL,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_comments_post FOREIGN KEY (post_id) REFERENCES posts (id),
    CONSTRAINT fk_comments_author FOREIGN KEY (author_id) REFERENCES users (id),
    CONSTRAINT fk_comments_parent FOREIGN KEY (parent_id) REFERENCES comments (id),
    CONSTRAINT fk_comments_reply_to_user FOREIGN KEY (reply_to_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_comments_post_parent ON comments (post_id, parent_id, id);
CREATE INDEX idx_comments_author ON comments (author_id);

CREATE TABLE post_likes (
    post_id    BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (post_id, user_id),
    CONSTRAINT fk_post_likes_post FOREIGN KEY (post_id) REFERENCES posts (id),
    CONSTRAINT fk_post_likes_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_post_likes_post_created ON post_likes (post_id, created_at, user_id);
CREATE INDEX idx_post_likes_user ON post_likes (user_id);
