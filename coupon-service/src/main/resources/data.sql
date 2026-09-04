INSERT IGNORE INTO coupon (id, title, total_quantity, issued_quantity, discount_type, discount_value, max_discount_amount, min_order_amount, event_end_at)
VALUES (1, '선착순 쿠폰', 100, 0, 'PERCENT', 10, NULL, NULL, DATE_ADD(NOW(), INTERVAL 1 YEAR)),
       (2, '10만원 한도 15% 할인', 50, 0, 'PERCENT', 15, 100000, NULL, DATE_ADD(NOW(), INTERVAL 1 YEAR)),
       (3, '5천원 할인', 200, 0, 'FIXED_AMOUNT', 5000, NULL, 30000, DATE_ADD(NOW(), INTERVAL 1 YEAR)),
       (4, '20만원 할인', 20, 0, 'FIXED_AMOUNT', 200000, NULL, 1000000, DATE_ADD(NOW(), INTERVAL 1 YEAR));
