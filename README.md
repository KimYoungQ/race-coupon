# raceCoupon — 선착순 쿠폰 발급 시스템

선착순 한정 수량 쿠폰 발급에서 발생하는 **동시성 문제(Race Condition)** 를 직접 재현하고
단계별로 해결해 나가는 학습 프로젝트입니다.

<br>
<br>

## 개요

대규모 트래픽 환경에서 선착순 쿠폰을 정확하게 발급하기 위한 시스템입니다.
레이스 컨디션을 의도적으로 재현하고, 이를 해결하는 과정을 단계별로 구현했습니다.
먼저 비관적 락으로 발급 정확성을 보장한 뒤, DB 락 병목을 줄이기 위해 Redis 원자 연산과 Kafka 비동기 처리를 적용해 성능과 확장성을 개선했습니다. 현재는 멀티모듈 아키텍처로 분리하여 서비스의 확장성과 운영성을 높이는 방향으로 고도화하고 있습니다.

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

## 쿠폰 발급 시퀀스

<img width="1437" height="687" alt="image" src="https://github.com/user-attachments/assets/e4e9973e-db85-4731-ab00-875d7486fd52" />

<br>
<br>

## 트러블슈팅 & 회고

- [동시성 문제 해결 전략 - 비관적 락](https://complete-hurricane-1d5.notion.site/394eca6f192b800f8c2ad27ab4e914a0?source=copy_link)
- [동시성 문제 해결 전략 - Redis](https://complete-hurricane-1d5.notion.site/Redis-394eca6f192b809bac89d908a81b36c8?source=copy_link)
- [부하 테스트 기반 쿠폰 발급 시스템 성능 개선 및 동시성 제어 검증 (Redis + Kafka)](https://complete-hurricane-1d5.notion.site/Redis-Kafka-39beca6f192b8122bdd2c77dd36e81e0?source=copy_link)
- [멀티모듈 분리 + Kafka 프로듀서·컨슈머 튜닝으로 처리량 개선](https://complete-hurricane-1d5.notion.site/Kafka-39eeca6f192b81d0babec4c18be91e00?source=copy_link)

<br>
<br>
