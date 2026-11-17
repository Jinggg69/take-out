local limit = tonumber(ARGV[1])
local window = tonumber(ARGV[2])
if not limit or limit <= 0 or limit % 1 ~= 0 then
    return redis.error_reply('invalid rate limit')
end
if not window or window <= 0 or window % 1 ~= 0 then
    return redis.error_reply('invalid rate limit window')
end

local raw = redis.call('GET', KEYS[1])
if not raw then
    redis.call('INCR', KEYS[1])
    redis.call('PEXPIRE', KEYS[1], window)
    return {1, window}
end

local count = tonumber(raw)
if not count or count < 0 or count % 1 ~= 0 then
    return redis.error_reply('invalid rate limit count')
end
local ttl = redis.call('PTTL', KEYS[1])
if ttl <= 0 then
    return redis.error_reply('rate limit key has no valid ttl')
end
if count >= limit then
    return {0, ttl}
end
redis.call('INCR', KEYS[1])
return {1, ttl}
