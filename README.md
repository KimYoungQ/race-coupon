# raceCoupon — 선착순 쿠폰 발급 시스템

선착순 한정 수량 쿠폰 발급에서 발생하는 **동시성 문제(Race Condition)** 를 직접 재현하고
단계별로 해결해 나가는 학습 프로젝트입니다.

<br>
<br>

## 개요

 대규모 트래픽 환경에서 선착순 쿠폰을 정확하게 발급하기 위한 시스템입니다.
레이스 컨디션을 의도적으로 재현하고, 이를 해결하는 과정을 단계별로 구현했습니다.
먼저 비관적 락으로 발급 정확성을 보장한 뒤, DB 락 병목을 줄이기 위해 Redis 원자 연산과 Kafka 비동기 처리를 적용해 성능과 확장성을 개선했습니다. 현재는 MSA 아키텍처 전환하여 서비스의 확장성과 운영성을 높이는 방향으로 고도화하고 있습니다. 
<br>
<br>
 자세한 내용은 [WIKI](https://github.com/KimYoungQ/race-coupon/wiki) 통해 참고하실 수 있습니다.
<br>
<br>

## 기술 스택

| 구분 | 기술 |
|------|------|
| Language | Java 17 |
| Framework | Spring Boot 4.1.0 |
| Persistence | Spring Data JPA, QueryDSL 5.1.0 |
| Database | MySQL 8.0 |
| In-Memory | Redis|
| Messaging | Kafka |
| Build | Gradle |
| Infra | Docker Compose |

<br>
<br>

## 프로젝트 구성도

