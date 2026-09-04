CREATE TABLE IF NOT EXISTS coupon (
    id                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '쿠폰 ID (PK)',
    title               VARCHAR(255) NOT NULL COMMENT '쿠폰 이름/제목',
    total_quantity      BIGINT       NOT NULL COMMENT '쿠폰 총 발행 수량',
    issued_quantity     BIGINT       NOT NULL COMMENT '현재까지 발행된 쿠폰 수량',
    discount_type       ENUM('FIXED_AMOUNT', 'PERCENT') NOT NULL COMMENT '할인 타입 (FIXED_AMOUNT: 정액, PERCENT: 퍼센트)',
    discount_value      BIGINT       NOT NULL COMMENT '할인값 (타입에 따라 정액 또는 퍼센트)',
    max_discount_amount BIGINT       DEFAULT NULL COMMENT '최대 할인 금액 (PERCENT 타입에서만 사용)',
    min_order_amount    BIGINT       DEFAULT NULL COMMENT '쿠폰 적용 최소 주문 금액',
    event_end_at        DATETIME(6)  NOT NULL COMMENT '쿠폰 이벤트 종료 날짜/시간',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='쿠폰 정보 저장 테이블';

CREATE TABLE IF NOT EXISTS issued_coupon (
    id        BIGINT      NOT NULL AUTO_INCREMENT COMMENT '발행된 쿠폰 ID (PK)',
    user_id   BIGINT      NOT NULL COMMENT '사용자 ID',
    coupon_id BIGINT      NOT NULL COMMENT '쿠폰 ID',
    status    ENUM('ISSUED', 'USED') NOT NULL COMMENT '쿠폰 상태 (ISSUED: 발행됨, USED: 사용함)',
    order_id  BIGINT      DEFAULT NULL COMMENT '쿠폰을 사용한 주문 ID',
    issued_at DATETIME(6) NOT NULL COMMENT '쿠폰 발행 날짜/시간',
    used_at   DATETIME(6) DEFAULT NULL COMMENT '쿠폰 사용 날짜/시간',
    PRIMARY KEY (id),
    UNIQUE KEY uk_issued_coupon_user_coupon (user_id, coupon_id),
    UNIQUE KEY uk_issued_coupon_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='발행된 쿠폰 저장 테이블 (사용자당 쿠폰 1장만 발행 가능)';

CREATE TABLE IF NOT EXISTS outbox_event (
    id            CHAR(36)     NOT NULL COMMENT '이벤트 ID (UUID)',
    aggregatetype VARCHAR(255) NOT NULL COMMENT 'Aggregate 타입 (ex: Coupon, IssuedCoupon)',
    aggregateid   VARCHAR(255) NOT NULL COMMENT 'Aggregate ID',
    type          VARCHAR(255) NOT NULL COMMENT '이벤트 타입 (ex: CouponIssued, CouponUsed)',
    payload       JSON         NOT NULL COMMENT '이벤트 페이로드 (JSON)',
    created_at    DATETIME(6)  NOT NULL COMMENT '이벤트 발행 날짜/시간',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Transactional Outbox 패턴: Debezium이 읽어 Kafka로 발행';

CREATE TABLE IF NOT EXISTS consumed_message (
    event_id        CHAR(36)    NOT NULL COMMENT 'Kafka 메시지 ID (발행 측 outbox_event.id)',
    time_of_receipt DATETIME(6) NOT NULL COMMENT '메시지 처리 완료 날짜/시간',
    PRIMARY KEY (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='처리된 Kafka 메시지 원장 (재전송 멱등성 확인용)';
