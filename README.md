# raceCoupon — 선착순 쿠폰 발급 시스템

선착순 한정 수량 쿠폰 발급에서 발생하는 **동시성 문제(Race Condition)** 를 직접 재현하고
단계별로 해결해 나가는 학습 프로젝트입니다.

<br>
<br>

## 프로젝트 소개

1000명이 100개 한정 쿠폰을 **동시에** 요청하면, 아무 제어가 없을 때 100개를 초과해 발급되는
버그가 발생합니다. 이 프로젝트는 그 문제를 **의도적으로 재현**하고 비관적 락으로 해결하며,
나아가 **대규모 트래픽에도 정확하게 동작하는 선착순 쿠폰 발급 시스템**을 목표로 합니다.

동시성 제어(비관적 락)로 정확성을 확보한 뒤, DB 락에 부하가 집중되는 한계를 넘어서기 위해
**Redis 기반 발급 카운팅**과 **Kafka 비동기 발급 파이프라인**까지 구현했으며,
다음으로 **멀티모듈 아키텍처** 분리를 진행합니다.

- **동시성 제어** — 레이스 컨디션 재현 후 비관적 락(`SELECT ... FOR UPDATE`)으로 정확히 100개 발급 보장
- **성능 확장** — Redis `INCR` 원자적 카운팅(Lua 스크립트 TTL)으로 DB 락 없이 정합성 확보
- **비동기 처리** — Kafka Producer/Consumer로 발급 요청과 DB 저장을 분리, 수동 커밋(manual ack)으로 안전하게 처리
- **구조 분리(예정)** — API 서버와 Consumer를 멀티모듈로 분리

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
| API 문서 | SpringDoc OpenAPI (Swagger UI) |
| Test | JUnit 5, AssertJ, Mockito, `java.util.concurrent` (ExecutorService, CountDownLatch) |

<br>
<br>

## 동시성 전략

| 버전 | 방식 | 기대 결과 |
|------|------|-----------|
| **V1** | 제어 없음 (`@Transactional`만) | 1000 스레드 → **100개 초과 발급** (문제 재현) |
| **V2** | 비관적 락 (`SELECT ... FOR UPDATE`) | **정확히 100개** (DB row 락으로 직렬화) |
| **V3** | Redis 원자적 카운팅 (`INCR`) | **정확히 100개** (DB 락 없이 처리량 확보) |
| **V4** | Redis 게이트 + Kafka 비동기 발급 | **정확히 100개** (DB 저장까지 비동기 분리) |

> **비관적 락의 한계**: 정확성은 보장되지만 발급 요청마다 DB row lock에 부하가 집중돼 트래픽이 커질수록 성능이 저하됩니다. 이 병목을 **Redis `INCR` 카운팅**으로 옮겨 DB 락 없이 해소했습니다(V3). 카운트 키에는 Lua 스크립트로 하루 TTL을 원자적으로 부여합니다.
>
> **V4**에서는 정확성(정확히 100개)은 그대로 Redis `INCR`가 보장하되, 실제 DB 저장을 **Kafka로 비동기 분리**했습니다. 발급 요청 스레드는 DB I/O를 기다리지 않고 즉시 응답하고, Consumer가 메시지를 소비해 `IssuedCoupon`을 저장합니다(수동 커밋으로 처리 성공 시에만 offset commit).

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
