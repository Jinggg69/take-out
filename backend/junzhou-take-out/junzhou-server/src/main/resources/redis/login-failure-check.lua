local limit = tonumber(ARGV[1])
if not limit or limit <= 0 or limit % 1 ~= 0 then
    return redis.error_reply('invalid login failure limit')
end
local raw = redis.call('GET', KEYS[1])
if not raw then
    return {1, 0}
end
local count = tonumber(raw)
if not count or count < 0 or count % 1 ~= 0 then
    return redis.error_reply('invalid login failure count')
end
local ttl = redis.call('PTTL', KEYS[1])
if ttl <= 0 then
    return redis.error_reply('login failure key has no valid ttl')
end
if count >= limit then
    return {0, ttl}
end
return {1, ttl}
