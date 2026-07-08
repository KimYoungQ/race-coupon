# raceCoupon — 선착순 쿠폰 발급 시스템

선착순 한정 수량 쿠폰 발급에서 발생하는 **동시성 문제(Race Condition)** 를 직접 재현하고
단계별로 해결해 나가는 학습 프로젝트입니다.

<br>
<br>

## 프로젝트 소개

1000명이 100개 한정 쿠폰을 **동시에** 요청하면, 아무 제어가 없을 때 100개를 초과해 발급되는
버그가 발생합니다. 이 프로젝트는 그 문제를 **의도적으로 재현**하고 비관적 락으로 해결하며,
나아가 **대규모 트래픽에도 정확하게 동작하는 선착순 쿠폰 발급 시스템**을 목표로 합니다.

동시성 제어(비관적 락)로 정확성을 확보한 뒤, DB 락에 부하가 집중되는 한계를 넘어
**Redis 기반 발급 카운팅**과 **Kafka 비동기 발급 파이프라인**까지 구현했으며,
다음으로 **멀티모듈 아키텍처** 분리를 진행합니다.

레이스 컨디션의 물리적 지점은 재고를 증가시키는 `read → +1 → write` 구간이며,
각 단계는 이 지점을 서로 다른 방식으로 직렬화·회피하도록 전략별 서비스 클래스를 분리해 비교합니다.

- **① 문제 재현** — 제어 없이 1000 스레드 동시 요청 시 100개 한정 쿠폰이 초과 발급되는 버그를 테스트로 입증
- **② 비관적 락** — `SELECT ... FOR UPDATE`(QueryDSL `setLockMode`)로 쿠폰 row를 직렬화해 정확히 100개 발급 보장
- **③ Redis 카운팅** — DB row 락 병목을 Redis `INCR` 원자적 연산으로 대체, 카운트 키 TTL은 Lua 스크립트로 `INCR`+`EXPIRE`를 원자적으로 처리(하루)
- **④ Kafka 비동기** — Redis로 수량을 확정한 뒤 DB 저장을 Kafka로 분리, 요청 스레드는 즉시 응답하고 Consumer가 수동 커밋(manual ack)으로 저장·재처리
- **⑤ 멀티모듈(예정)** — API 서버와 Consumer를 독립 모듈로 분리해 확장·배포·장애 격리

<br>
<br>

## 기술 스택

| 구분 | 기술 |
|------|------|
| Language | Java 17 |
| Framework | Spring Boot 4.1.0 |
| Persistence | Spring Data JPA, QueryDSL 5.1.0 |
| Database | MySQL 8.0 (운영), H2 (로컬 옵션) |
| In-Memory | Redis (Spring Data Redis, Lua Script) |
| Messaging | Apache Kafka (Spring for Apache Kafka) |
| Build | Gradle |
| Infra | Docker Compose |

<br>
<br>

## 트러블슈팅 & 회고

- [동시성 문제 해결 전략 - 비관적 락](https://complete-hurricane-1d5.notion.site/394eca6f192b800f8c2ad27ab4e914a0?source=copy_link)
- [동시성 문제 해결 전략 - Redis](https://complete-hurricane-1d5.notion.site/Redis-394eca6f192b809bac89d908a81b36c8?source=copy_link)

<br>
<br>

## 실행 방법

### 1. 환경 변수 준비
```bash
cp .env.example .env   # 값 채우기
```

### 2. 인프라 기동 (MySQL · Kafka · Kafka UI)
```bash
docker-compose up -d   # MySQL 8.0(coupon_service), Kafka, Kafka UI(localhost:8080)
```

### 3. Redis 기동
```bash
docker run --name myredis -d -p 6379:6379 redis   # 발급 카운팅용
```

### 4. 애플리케이션 실행
```bash
./gradlew bootRun      # 기본 포트 8081
```
<br>
<br>

## 테스트

```bash
./gradlew test
```

테스트는 JUnit 5 + AssertJ 기반이며(`@DisplayName` 한글, given/when/then),
동시성 테스트가 이 프로젝트의 핵심입니다. `ExecutorService` + `CountDownLatch(1000)` 로
1000건을 동시에 발급 요청한 뒤 실제 발급 건수(`countByCouponId`)를 검증합니다.

- V1: 발급 수 100 초과 → 레이스 컨디션 재현
- V2: 정확히 100개 → 비관적 락으로 해결
- V3: 정확히 100개 → Redis `INCR`로 DB 락 없이 해결
- V4: 정확히 100개 → Kafka 비동기 발급 (`@EmbeddedKafka`로 검증)

<br>
<br>

## 프로젝트 구조

```
src/main/java/org/coupon/racecoupon
├── config/       # QuerydslConfig, OpenApiConfig (Swagger)
├── controller/   # CouponIssueController (+ CouponIssueControllerApi 문서 인터페이스)
├── service/      # 동시성 전략 4종 (V1/V2/V3-Redis/V4-Kafka)
├── kafka/        # CouponIssueProducer, CouponIssueConsumer, CouponIssueMessage
├── repository/   # CouponRepository, IssuedCouponRepository, CouponCountRedisRepository (+ QueryDSL)
├── domain/       # Coupon, IssuedCoupon
├── dto/          # CouponIssueResponse
├── common/       # ApiResponse
└── exception/    # BusinessException, ErrorCode, CouponSoldOutException, CouponNotFoundException, GlobalExceptionHandler
```
