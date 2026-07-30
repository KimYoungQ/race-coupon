-- id=1은 선착순 발급 벤치마크 대상이므로 순서와 수량을 바꾸지 않는다.
INSERT INTO coupon (title, total_quantity, issued_quantity, discount_type, discount_value, max_discount_amount, min_order_amount)
VALUES ('선착순 쿠폰', 100, 0, 'PERCENT', 10, NULL, NULL),
       ('10만원 한도 15% 할인', 50, 0, 'PERCENT', 15, 100000, NULL),
       -- 정액 할인은 주문 금액과 무관하게 차감되므로 최소주문금액이 실질적 가드다.
       ('5천원 할인', 200, 0, 'FIXED_AMOUNT', 5000, NULL, 30000),
       ('20만원 할인', 20, 0, 'FIXED_AMOUNT', 200000, NULL, 1000000);
