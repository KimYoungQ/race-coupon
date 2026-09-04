CREATE TABLE IF NOT EXISTS users (
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '사용자 ID (PK)',
    username   VARCHAR(255) NOT NULL COMMENT '사용자명 (로그인 ID)',
    email      VARCHAR(255) NOT NULL COMMENT '사용자 이메일 주소',
    password   VARCHAR(255) NOT NULL COMMENT '암호화된 비밀번호',
    role       ENUM('ADMIN','USER') NOT NULL COMMENT '사용자 권한 (ADMIN/USER)',
    enabled    BIT(1)       NOT NULL COMMENT '계정 활성화 여부 (1:활성화, 0:비활성화)',
    created_at DATETIME(6)  NOT NULL COMMENT '사용자 생성 날짜/시간',
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username),
    UNIQUE KEY uk_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='사용자 정보 저장 테이블';

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'Refresh Token ID (PK)',
    user_id    BIGINT       NOT NULL COMMENT '사용자 ID',
    token      VARCHAR(512) NOT NULL COMMENT 'Refresh Token 값 (JWT)',
    expires_at DATETIME(6)  NOT NULL COMMENT 'Token 만료 날짜/시간',
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_tokens_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='사용자 Refresh Token 저장 테이블';
