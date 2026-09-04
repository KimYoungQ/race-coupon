CREATE TABLE IF NOT EXISTS product (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '상품 ID (PK)',
    name        VARCHAR(255) NOT NULL COMMENT '상품명',
    price       BIGINT       NOT NULL COMMENT '상품 가격',
    stock       BIGINT       NOT NULL COMMENT '현재 재고 수량',
    created_at  DATETIME(6)  NOT NULL COMMENT '상품 등록 날짜/시간',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='상품 정보 저장 테이블';

CREATE TABLE IF NOT EXISTS stock_reservation (
    id          BIGINT NOT NULL AUTO_INCREMENT COMMENT '재고 예약 ID (PK)',
    order_id    BIGINT NOT NULL COMMENT '주문 ID',
    product_id  BIGINT NOT NULL COMMENT '상품 ID',
    quantity    BIGINT NOT NULL COMMENT '예약된 재고 수량',
    status      ENUM('RESERVED','RESTORED') NOT NULL COMMENT '예약 상태 (RESERVED: 예약됨, RESTORED: 복구됨)',
    PRIMARY KEY (id),
    UNIQUE KEY uk_stock_reservation_order_id (order_id),
    KEY idx_stock_reservation_product (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='상품 재고 예약 저장 테이블 (동시성 제어: 주문당 1개만 예약)';

CREATE TABLE IF NOT EXISTS outbox_event (
    id            CHAR(36)     NOT NULL COMMENT '이벤트 ID (UUID)',
    aggregatetype VARCHAR(255) NOT NULL COMMENT 'Aggregate 타입 (ex: Product, Stock)',
    aggregateid   VARCHAR(255) NOT NULL COMMENT 'Aggregate ID',
    type          VARCHAR(255) NOT NULL COMMENT '이벤트 타입 (ex: StockReserved, StockRestored)',
    payload       JSON         NOT NULL COMMENT '이벤트 페이로드 (JSON)',
    created_at    DATETIME(6)  NOT NULL COMMENT '이벤트 발행 날짜/시간',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Transactional Outbox 패턴: Debezium이 읽어 Kafka로 발행';

CREATE TABLE IF NOT EXISTS consumed_message (
    event_id        CHAR(36)    NOT NULL COMMENT 'Kafka 메시지 ID (발행 측 outbox_event.id)',
    time_of_receipt DATETIME(6) NOT NULL COMMENT '메시지 처리 완료 날짜/시간',
    PRIMARY KEY (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='처리된 Kafka 메시지 원장 (재전송 멱등성 확인용)';
