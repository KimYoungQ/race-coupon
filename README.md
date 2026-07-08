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
**Redis 기반 발급 카운팅·중복 방지**, **Kafka 비동기 발급 파이프라인**, **멀티모듈 아키텍처**로
확장해 나갈 계획입니다.

- **동시성 제어** — 레이스 컨디션 재현 후 비관적 락(`SELECT ... FOR UPDATE`)으로 정확히 100개 발급 보장
- **성능 확장** — Redis `INCR` 카운팅 + Set 중복 방지로 DB 락 부하를 분산
- **비동기 처리** — Kafka Producer/Consumer로 발급 요청과 실제 생성을 분리, 실패 이벤트 백업·재처리
- **구조 분리** — API 서버와 Consumer를 멀티모듈로 분리

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

### 2. 데이터베이스 기동
```bash
docker-compose up -d   # MySQL 8.0 (DB: coupon_service)
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

<br>
<br>

## 프로젝트 구조

```
src/main/java/org/coupon/racecoupon
├── config/       # QuerydslConfig, OpenApiConfig (Swagger)
├── controller/   # CouponIssueController (+ CouponIssueControllerApi 문서 인터페이스)
├── service/      # 동시성 전략 3종 (V1/V2/V3-Redis)
├── repository/   # CouponRepository, IssuedCouponRepository (+ QueryDSL)
├── domain/       # Coupon, IssuedCoupon
├── dto/          # CouponIssueResponse
├── common/       # ApiResponse
└── exception/    # BusinessException, ErrorCode, CouponSoldOutException, CouponNotFoundException, GlobalExceptionHandler
```
