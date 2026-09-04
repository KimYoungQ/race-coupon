local seqKey    = KEYS[1]
local issuedKey = KEYS[2]

local userId = ARGV[1]
local limit  = tonumber(ARGV[2])
local ttl    = tonumber(ARGV[3])

if ttl <= 0 then
  return -2
end

if redis.call('SISMEMBER', issuedKey, userId) == 1 then
  return 0
end

local issuedCount = tonumber(redis.call('GET', seqKey) or "0")
if issuedCount >= limit then
  return -1
end

local mySeq = redis.call('INCR', seqKey)
redis.call('SADD', issuedKey, userId)

redis.call('EXPIRE', seqKey, ttl)
redis.call('EXPIRE', issuedKey, ttl)

return mySeq
