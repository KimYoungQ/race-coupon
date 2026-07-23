#!/bin/bash
# user-service 전용 스키마 생성 (Database per Service).
# MySQL 이미지는 MYSQL_DATABASE 하나만 만들어주므로 두 번째 스키마는 여기서 만든다.
#
# .sql이 아니라 .sh인 이유: 엔트리포인트는 .sql 파일에 환경변수를 치환해주지 않는다.
# MYSQL_USER를 GRANT에 써야 하므로 셸에서 처리해야 한다.
#
# 주의: 이 스크립트는 데이터 볼륨이 비어 있을 때만 실행된다.
# 이미 mysql-data 볼륨이 있으면 적용되지 않으므로 수동으로 같은 SQL을 실행해야 한다.
set -e

mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" <<-EOSQL
    CREATE DATABASE IF NOT EXISTS user_service
        CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
    GRANT ALL PRIVILEGES ON user_service.* TO '${MYSQL_USER}'@'%';
    FLUSH PRIVILEGES;
EOSQL
