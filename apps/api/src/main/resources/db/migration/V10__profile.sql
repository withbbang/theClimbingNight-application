-- users <-> profile_images 순환 FK: 탈퇴는 행을 지우지 않으므로 문제없음
ALTER TABLE users ADD COLUMN bio VARCHAR(150) NULL;
ALTER TABLE users ADD COLUMN profile_image_id BIGINT NULL;

CREATE TABLE profile_images (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    user_id       BIGINT       NOT NULL,
    status        VARCHAR(16)  NOT NULL,
    upload_key    VARCHAR(128) NOT NULL,
    public_key    VARCHAR(128) NOT NULL,
    original_path VARCHAR(255) NULL,
    byte_size     BIGINT       NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_profile_images_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_profile_images_user_status ON profile_images (user_id, status);
CREATE INDEX idx_profile_images_status_created ON profile_images (status, created_at);

ALTER TABLE users ADD CONSTRAINT fk_users_profile_image FOREIGN KEY (profile_image_id) REFERENCES profile_images (id);

ALTER TABLE media_jobs ADD COLUMN target_type VARCHAR(16) NOT NULL DEFAULT 'POST_MEDIA';
DROP INDEX idx_media_jobs_target ON media_jobs;
CREATE INDEX idx_media_jobs_target ON media_jobs (target_type, target_id, kind);

CREATE INDEX idx_posts_author_grid ON posts (author_id, status, published_at, id);
