CREATE TABLE IF NOT EXISTS orders (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '주문 ID (PK)',
    user_id         BIGINT       NOT NULL COMMENT '사용자 ID',
    status          ENUM('COMPENSATING','COMPLETED','CREATED','FAILED','STOCK_RESERVED') NOT NULL COMMENT '주문 상태 (CREATED/STOCK_RESERVED/COMPLETED/FAILED/COMPENSATING)',
    coupon_id       BIGINT       DEFAULT NULL COMMENT '적용된 쿠폰 ID',
    total_amount    BIGINT       NOT NULL COMMENT '전체 상품 금액 (할인 전)',
    discount_amount BIGINT       NOT NULL COMMENT '할인 금액',
    final_amount    BIGINT       NOT NULL COMMENT '최종 결제 금액 (total_amount - discount_amount)',
    failure_code    VARCHAR(255) DEFAULT NULL COMMENT '실패 원인 코드 (실패시에만 기록)',
    created_at      DATETIME(6)  NOT NULL COMMENT '주문 생성 날짜/시간',
    updated_at      DATETIME(6)  NOT NULL COMMENT '주문 상태 업데이트 날짜/시간',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='주문 정보 저장 테이블';

CREATE TABLE IF NOT EXISTS order_item (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '주문 항목 ID (PK)',
    order_id     BIGINT       NOT NULL COMMENT '주문 ID',
    product_id   BIGINT       NOT NULL COMMENT '상품 ID',
    product_name VARCHAR(255) DEFAULT NULL COMMENT '상품명 (주문 당시의 상품명 스냅샷)',
    unit_price   BIGINT       DEFAULT NULL COMMENT '상품 단가 (주문 당시의 가격 스냅샷)',
    quantity     INT          NOT NULL COMMENT '상품 수량',
    PRIMARY KEY (id),
    KEY idx_order_item_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='주문 항목 저장 테이블';

CREATE TABLE IF NOT EXISTS outbox_event (
    id            CHAR(36)     NOT NULL COMMENT '이벤트 ID (UUID)',
    aggregatetype VARCHAR(255) NOT NULL COMMENT 'Aggregate 타입 (ex: Order, Coupon, Stock)',
    aggregateid   VARCHAR(255) NOT NULL COMMENT 'Aggregate ID',
    type          VARCHAR(255) NOT NULL COMMENT '이벤트 타입 (ex: OrderCreated, OrderCompleted)',
    payload       JSON         NOT NULL COMMENT '이벤트 페이로드 (JSON)',
    created_at    DATETIME(6)  NOT NULL COMMENT '이벤트 발행 날짜/시간',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Transactional Outbox 패턴: Debezium이 읽어 Kafka로 발행';

CREATE TABLE IF NOT EXISTS consumed_message (
    event_id        CHAR(36)    NOT NULL COMMENT 'Kafka 메시지 ID (발행 측 outbox_event.id)',
    time_of_receipt DATETIME(6) NOT NULL COMMENT '메시지 처리 완료 날짜/시간',
    PRIMARY KEY (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='처리된 Kafka 메시지 원장 (재전송 멱등성 확인용)';

CREATE TABLE IF NOT EXISTS saga_state (
    id           CHAR(36)     NOT NULL COMMENT '사가 실행 ID (UUID)',
    order_id     BIGINT       NOT NULL COMMENT '주문 ID (주문당 사가 1개)',
    version      INT          NOT NULL COMMENT '사가 실행 버전 (낙관적 락)',
    type         VARCHAR(100) NOT NULL COMMENT '사가 타입 (ex: OrderSaga)',
    payload      JSON         NOT NULL COMMENT '사가 초기 페이로드',
    current_step VARCHAR(100) DEFAULT NULL COMMENT '현재 실행 중인 사가 스텝',
    step_status  JSON         NOT NULL COMMENT '각 스텝별 실행 상태 (ex: {\"stock-reservation\":\"SUCCEEDED\"})',
    status       VARCHAR(20)  NOT NULL COMMENT '사가 상태 (STARTED/COMPLETED/FAILED)',
    created_at   DATETIME(6)  NOT NULL COMMENT '사가 시작 날짜/시간',
    updated_at   DATETIME(6)  NOT NULL COMMENT '사가 상태 변경 날짜/시간',
    PRIMARY KEY (id),
    UNIQUE KEY uk_saga_state_order_id (order_id),
    KEY idx_saga_state_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Saga 패턴: 분산 트랜잭션 조율 상태 저장';
