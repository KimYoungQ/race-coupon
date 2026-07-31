-- id=1은 선착순 발급 벤치마크 대상이므로 순서와 수량을 바꾸지 않는다.
-- event_end_at은 Redis 발급 키의 TTL 기준이다. 과거면 발급이 전부 409로 떨어지므로
-- 로컬 시드는 기동 시점에서 1년 뒤로 민다.
INSERT INTO coupon (title, total_quantity, issued_quantity, discount_type, discount_value, max_discount_amount, min_order_amount, event_end_at)
VALUES ('선착순 쿠폰', 100, 0, 'PERCENT', 10, NULL, NULL, DATE_ADD(NOW(), INTERVAL 1 YEAR)),
       ('10만원 한도 15% 할인', 50, 0, 'PERCENT', 15, 100000, NULL, DATE_ADD(NOW(), INTERVAL 1 YEAR)),
       -- 정액 할인은 주문 금액과 무관하게 차감되므로 최소주문금액이 실질적 가드다.
       ('5천원 할인', 200, 0, 'FIXED_AMOUNT', 5000, NULL, 30000, DATE_ADD(NOW(), INTERVAL 1 YEAR)),
       ('20만원 할인', 20, 0, 'FIXED_AMOUNT', 200000, NULL, 1000000, DATE_ADD(NOW(), INTERVAL 1 YEAR));
