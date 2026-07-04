# raceCoupon — 선착순 쿠폰 발급 시스템

선착순 한정 수량 쿠폰 발급에서 발생하는 **동시성 문제(Race Condition)** 를 직접 재현하고
단계별로 해결해 나가는 학습 프로젝트입니다.

> 벤치마크: Inflearn [실습으로 배우는 선착순 이벤트 시스템](https://inf.run/4Hoa)

---

## 프로젝트 소개

1000명이 100개 한정 쿠폰을 **동시에** 요청하면, 아무 제어가 없을 때 100개를 초과해 발급되는
버그가 발생합니다. 이 프로젝트는 그 문제를 **의도적으로 재현**한 뒤,
비관적 락(DB)으로 해결하는 과정을 커밋 단위로 남기는 것을 목표로 합니다.

### 핵심 포인트
- 발급 수량 증가(`read → +1 → write`) 지점이 레이스 컨디션의 물리적 위치
- 동시성 해결 전략을 **서비스 클래스별로 분리**해 비교 학습
- 각 전략마다 1000 스레드 동시 요청 테스트로 결과 검증

---

## 기술 스택

| 구분 | 기술 |
|------|------|
| Language | Java 17 |
| Framework | Spring Boot 4.1.0 |
| Persistence | Spring Data JPA, QueryDSL 5.1.0 |
| Database | MySQL 8.0 (운영), H2 (로컬 옵션) |
| Build | Gradle |
| Infra | Docker Compose |
| API 문서 | SpringDoc OpenAPI (Swagger UI) |
| Test | JUnit 5, AssertJ, Mockito, `java.util.concurrent` (ExecutorService, CountDownLatch) |

---

## 아키텍처

```
Controller → Service → Repository → Database
```

- **Controller**: HTTP 입출력만 담당 (GET / POST)
- **Service**: 발급 흐름 조율, 동시성 전략별로 분리
- **Domain(Coupon)**: 재고 검증·발급 규칙을 스스로 보유
- **Repository**: JPA + QueryDSL

### 도메인 모델

| 엔티티 | 설명 |
|--------|------|
| `Coupon` | 재고를 가진 쿠폰. `totalQuantity` / `issuedQuantity` 보유, 발급 규칙의 잠금 대상 |
| `IssuedCoupon` | 발급 이력 (누가·어떤 쿠폰·언제). 발급 1건 = 1 row |

> `Coupon` 1 : N `IssuedCoupon` 관계이며, **DB 외래키(FK) 제약 없이** `couponId` 값 참조로 연결합니다.

---

## 동시성 전략

| 버전 | 방식 | 기대 결과 |
|------|------|-----------|
| **V1** | 제어 없음 (`@Transactional`만) | 1000 스레드 → **100개 초과 발급** (문제 재현) |
| **V2** | 비관적 락 (`SELECT ... FOR UPDATE`) | **정확히 100개** (해결) |

> **V2의 한계**: 정확성은 보장되지만 발급 요청마다 DB row lock에 부하가 집중돼 트래픽이 커질수록 성능이 저하됩니다. 이 병목이 Phase 2에서 **Redis** 로 넘어가는 이유입니다.

---

## API 명세

HTTP 메서드는 **GET / POST** 만 사용합니다.
API 문서화와 동작 확인은 **Swagger UI** 에서 진행합니다: `http://localhost:8081/swagger-ui.html`

### 쿠폰 발급
```
POST /api/v1/coupons/{couponId}/issue?userId={userId}&strategy={none|pessimistic}
```
- `strategy` 생략 시 기본값은 해결 전략(V2)
- 성공: `201 Created`
- 재고 소진: `409 Conflict`

### 잔여 수량 조회
```
GET /api/v1/coupons/{couponId}
```
- 성공: `200 OK` (couponId, issuedQuantity, remaining)

---

## 실행 방법

### 1. 환경 변수 준비
```bash
cp .env.example .env   # 값 채우기
```

### 2. 데이터베이스 기동
```bash
docker-compose up -d   # MySQL 8.0 (DB: coupon_service)
```

### 3. 애플리케이션 실행
```bash
./gradlew bootRun      # 기본 포트 8081
```

### 4. 동작 확인 (Swagger UI)
브라우저에서 `http://localhost:8081/swagger-ui.html` 접속 후 API를 직접 실행합니다.

```
POST /api/v1/coupons/{couponId}/issue   # 쿠폰 발급
GET  /api/v1/coupons/{couponId}         # 잔여 수량 조회
```

---

## 테스트

```bash
./gradlew test
```

테스트는 JUnit 5 + AssertJ 기반이며(`@DisplayName` 한글, given/when/then),
동시성 테스트가 이 프로젝트의 핵심입니다. `ExecutorService` + `CountDownLatch(1000)` 로
1000건을 동시에 발급 요청한 뒤 실제 발급 건수(`countByCouponId`)를 검증합니다.

- V1: 발급 수 100 초과 → 레이스 컨디션 재현
- V2: 정확히 100개 → 비관적 락으로 해결

---

## 프로젝트 구조

```
src/main/java/org/coupon/racecoupon
├── config/       # QuerydslConfig, OpenApiConfig (Swagger)
├── controller/   # CouponIssueController (+ CouponIssueControllerApi 문서 인터페이스)
├── service/      # 동시성 전략 2종 (V1/V2)
├── repository/   # CouponRepository, IssuedCouponRepository (+ QueryDSL)
├── domain/       # Coupon, IssuedCoupon
├── dto/          # CouponIssueResponse
└── exception/    # CouponSoldOutException, GlobalExceptionHandler
```

---

## Phase 2 계획 (예정)

1차에서 단일 서버 기준 동시성 제어를 다룬 뒤, 2차에서는 다음을 적용할 예정입니다.

- **Redis** 를 도입해 발급 수량 카운팅(`INCR`)과 중복 발급 방지(Set)를 처리할 예정입니다.
- **Kafka** 로 발급 요청과 실제 쿠폰 생성을 비동기 분리(Producer / Consumer)할 예정입니다.
- 발급 실패 이벤트 백업 및 재처리 구조를 추가할 예정입니다.
- API 서버와 Consumer 를 **멀티모듈**로 분리할 예정입니다.

즉, 최종적으로는 원본 `coupon-system` 과 동일하게 Redis + Kafka 기반의 확장 구조로 발전시킬 예정입니다.
