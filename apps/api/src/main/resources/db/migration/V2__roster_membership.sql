CREATE TABLE roster_members (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    somoim_nickname VARCHAR(50)  NOT NULL,
    user_id         BIGINT       NULL,
    note            VARCHAR(255) NULL,
    source          VARCHAR(16)  NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_roster_members_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uk_roster_members_user UNIQUE (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_roster_members_nickname ON roster_members (somoim_nickname);

CREATE TABLE membership_requests (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    user_id          BIGINT       NOT NULL,
    somoim_nickname  VARCHAR(50)  NOT NULL,
    status           VARCHAR(16)  NOT NULL,
    reject_reason    VARCHAR(200) NULL,
    roster_member_id BIGINT       NULL,
    reviewed_by      BIGINT       NULL,
    reviewed_at      DATETIME(6)  NULL,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_membership_requests_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_membership_requests_roster FOREIGN KEY (roster_member_id) REFERENCES roster_members (id),
    CONSTRAINT fk_membership_requests_reviewer FOREIGN KEY (reviewed_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_membership_requests_status ON membership_requests (status, id);
CREATE INDEX idx_membership_requests_user ON membership_requests (user_id, id);
