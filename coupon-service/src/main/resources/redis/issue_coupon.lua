-- ============================================================
-- 선착순 쿠폰 발급 — 자격 검증 스크립트
--
-- KEYS[1] : 일련번호 카운터 키   예) coupon:1001:seq
-- KEYS[2] : 발급자 집합 키       예) coupon:1001:issued
--
-- ARGV[1] : 사용자 ID            예) 42
-- ARGV[2] : 최대 발급 수량       예) 500
-- ARGV[3] : 두 키에 걸 TTL(초)   예) 13600
--
-- 반환값
--    양수 n : 발급 성공. n 이 확정된 누적 발급 수
--    0      : 이미 발급받은 사용자 (1인 1매 위반)
--   -1      : 수량 소진 (마감)
--   -2      : TTL 이 유효하지 않음 (이벤트 종료 이후)
-- ============================================================

local seqKey    = KEYS[1]
local issuedKey = KEYS[2]

local userId = ARGV[1]
local limit  = tonumber(ARGV[2])
local ttl    = tonumber(ARGV[3])

-- [0단계] TTL 방어
-- EXPIRE 에 0 이하를 넘기면 Redis 는 값을 만료시키는 게 아니라 키를 즉시 삭제한다.
-- 그대로 두면 발급을 처리한 직후 두 키가 사라지고, 다음 요청은 카운터 0 에서 시작해
-- 전량이 한 번 더 나간다. 호출부가 이미 걸러내지만 되돌릴 수 없는 사고라 여기서 다시 막는다.
if ttl <= 0 then
  return -2
end

-- [1단계] 중복 검증
-- 이미 받은 사용자면 카운터를 건드리지 않고 즉시 빠져나간다.
-- 순서가 중요하다. 중복 검사를 먼저 해야 재시도가 번호를 낭비하지 않는다.
if redis.call('SISMEMBER', issuedKey, userId) == 1 then
  return 0
end

-- [2단계] 수량 검증
-- 스크립트 전체가 원자적으로 실행되므로, 여기서 GET 후 비교해도 안전하다.
-- 이 사이에 다른 클라이언트가 끼어들 수 없다.
-- 키가 아직 없으면 GET 은 false 를 반환하므로 "0" 으로 대체한다.
local issuedCount = tonumber(redis.call('GET', seqKey) or "0")
if issuedCount >= limit then
  return -1
end

-- [3단계] 발급 확정
-- 검증을 모두 통과한 요청만 카운터를 올린다.
-- 따라서 이 카운터는 "시도 수"가 아니라 진짜 "발급 수"가 된다.
local mySeq = redis.call('INCR', seqKey)
redis.call('SADD', issuedKey, userId)

-- [4단계] TTL 보장
-- 두 키에 같은 만료를 건다. 한쪽만 먼저 사라지면 중복 발급 또는 카운터 초기화다.
redis.call('EXPIRE', seqKey, ttl)
redis.call('EXPIRE', issuedKey, ttl)

return mySeq
