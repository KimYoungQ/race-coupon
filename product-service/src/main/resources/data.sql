INSERT IGNORE INTO product (id, name, price, stock, created_at)
VALUES (1, '노트북', 1500000, 100, NOW()),
       (2, '무선 이어폰', 200000, 50, NOW()),
       (3, '한정판 키보드', 350000, 10, NOW());
