# raceCoupon — 선착순 쿠폰 발급 시스템

대규모 동시 요청 환경에서 선착순 쿠폰을 안정적으로 발급하기 위한 MSA 기반 프로젝트입니다.  
동시성 문제를 해결하는 과정을 단계적으로 구현하며, 정확한 쿠폰 발급과 확장 가능한 서비스 구조를 고민했습니다.  
서비스 분리, 비동기 처리, 운영 환경 구성을 통해 실제 서비스에 가까운 시스템 설계를 목표로 합니다.
<br>
<br>
 자세한 내용은 [WIKI](https://github.com/KimYoungQ/race-coupon/wiki) 통해 참고하실 수 있습니다.

> **테스트 실행 요구사항** — `./gradlew test`는 Docker 데몬이 떠 있어야 합니다. 테스트 DB는 Testcontainers가 `mysql:8.0`을 띄우고 지웁니다(운영과 같은 `schema.sql` 사용). `coupon-service`의 선착순 발급 테스트는 로컬 Redis(`docker compose up -d redis`)도 필요합니다. 스키마를 바꿨으면 `docker compose down -v`로 MySQL 볼륨을 재생성합니다.
<br>
<br>
<br>



