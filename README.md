# raceCoupon — 선착순 쿠폰 발급 시스템

대규모 동시 요청 환경에서 선착순 쿠폰을 안정적으로 발급하기 위한 MSA 기반 프로젝트입니다.  
동시성 문제를 해결하는 과정을 단계적으로 구현하며, 정확한 쿠폰 발급과 확장 가능한 서비스 구조를 고민했습니다.  
서비스 분리, 비동기 처리, 운영 환경 구성을 통해 실제 서비스에 가까운 시스템 설계를 목표로 합니다.
<br>
<br>
<br>
<br>
### 쿠폰 발급 flow
쿠폰 발급 시 Redis 상태를 RESERVED와 ISSUED로 나누어 관리하여 Redis와 Kafka 간 데이터 일관성을 유지합니다.<br>
Redis 검증 및 발급 이후 Kafka 메시지 발행에 실패하는 등의 예외 상황으로 남은 RESERVED 상태는 <br>
복구 스케줄러가 주기적으로 확인하여 재처리하도록 하였습니다. <br><br>
<img width="921" height="379" alt="image" src="https://github.com/user-attachments/assets/1a2e8cc2-b9da-440f-bd14-c2c78f37e201" />




