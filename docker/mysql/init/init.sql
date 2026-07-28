CREATE DATABASE IF NOT EXISTS coupon_service  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS user_service    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS product_service CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS order_service   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

GRANT ALL PRIVILEGES ON coupon_service.*  TO 'cpuser'@'%';
GRANT ALL PRIVILEGES ON user_service.*    TO 'cpuser'@'%';
GRANT ALL PRIVILEGES ON product_service.* TO 'cpuser'@'%';
GRANT ALL PRIVILEGES ON order_service.*   TO 'cpuser'@'%';

FLUSH PRIVILEGES;
