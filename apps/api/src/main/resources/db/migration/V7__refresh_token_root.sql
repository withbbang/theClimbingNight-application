-- 갱신으로 이어진 토큰이 가리키는 첫 토큰. 첫 토큰과 이 컬럼이 생기기 전의 토큰은 NULL.
ALTER TABLE refresh_tokens ADD COLUMN root_id BIGINT NULL;

CREATE INDEX idx_refresh_tokens_root_id ON refresh_tokens (root_id);
