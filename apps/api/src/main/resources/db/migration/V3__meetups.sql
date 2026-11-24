CREATE TABLE meetups (
    id                     BIGINT       NOT NULL AUTO_INCREMENT,
    title                  VARCHAR(100) NOT NULL,
    description            TEXT         NULL,
    place_name             VARCHAR(100) NULL,
    starts_at              DATETIME(6)  NOT NULL,
    ends_at                DATETIME(6)  NULL,
    capacity               INT          NULL,
    late_cancel_hours      INT          NOT NULL DEFAULT 24,
    participants_visible   BOOLEAN      NOT NULL DEFAULT TRUE,
    host_user_id           BIGINT       NULL,
    status                 VARCHAR(16)  NOT NULL,
    canceled_at            DATETIME(6)  NULL,
    source                 VARCHAR(16)  NOT NULL,
    attendance_recorded_at DATETIME(6)  NULL,
    created_at             DATETIME(6)  NOT NULL,
    updated_at             DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_meetups_host FOREIGN KEY (host_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_meetups_starts_at ON meetups (starts_at);
CREATE INDEX idx_meetups_attendance_due ON meetups (status, attendance_recorded_at, ends_at);

CREATE TABLE meetup_applications (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    meetup_id        BIGINT       NOT NULL,
    roster_member_id BIGINT       NOT NULL,
    status           VARCHAR(16)  NOT NULL,
    applied_at       DATETIME(6)  NOT NULL,
    confirmed_at     DATETIME(6)  NULL,
    canceled_at      DATETIME(6)  NULL,
    late_cancel      BOOLEAN      NOT NULL DEFAULT FALSE,
    cancel_reason    VARCHAR(200) NULL,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_meetup_applications_meetup FOREIGN KEY (meetup_id) REFERENCES meetups (id),
    CONSTRAINT fk_meetup_applications_roster FOREIGN KEY (roster_member_id) REFERENCES roster_members (id),
    CONSTRAINT uk_meetup_applications_meetup_roster UNIQUE (meetup_id, roster_member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_meetup_applications_roster ON meetup_applications (roster_member_id, status);

CREATE TABLE attendances (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    meetup_id        BIGINT      NOT NULL,
    roster_member_id BIGINT      NOT NULL,
    result           VARCHAR(16) NOT NULL,
    recorded_by      BIGINT      NULL,
    source           VARCHAR(16) NOT NULL,
    created_at       DATETIME(6) NOT NULL,
    updated_at       DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_attendances_meetup FOREIGN KEY (meetup_id) REFERENCES meetups (id),
    CONSTRAINT fk_attendances_roster FOREIGN KEY (roster_member_id) REFERENCES roster_members (id),
    CONSTRAINT fk_attendances_recorder FOREIGN KEY (recorded_by) REFERENCES users (id),
    CONSTRAINT uk_attendances_meetup_roster UNIQUE (meetup_id, roster_member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_attendances_roster ON attendances (roster_member_id);

CREATE TABLE penalties (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    roster_member_id BIGINT       NOT NULL,
    meetup_id        BIGINT       NOT NULL,
    type             VARCHAR(16)  NOT NULL,
    created_by       BIGINT       NOT NULL,
    revoked_at       DATETIME(6)  NULL,
    revoked_by       BIGINT       NULL,
    revoke_reason    VARCHAR(200) NULL,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_penalties_roster FOREIGN KEY (roster_member_id) REFERENCES roster_members (id),
    CONSTRAINT fk_penalties_meetup FOREIGN KEY (meetup_id) REFERENCES meetups (id),
    CONSTRAINT fk_penalties_creator FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT fk_penalties_revoker FOREIGN KEY (revoked_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_penalties_roster ON penalties (roster_member_id, revoked_at);
CREATE INDEX idx_penalties_meetup ON penalties (meetup_id, roster_member_id, type);
