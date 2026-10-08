package com.sky;

import com.sky.config.RedisConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Spring Data Redis 集成测试，运行前需要启动 Redis。
 * 仅加载 Redis 配置，连接参数沿用 application.yml 和开发环境配置。
 */
@SpringBootTest(
        classes = {RedisConfiguration.class, RedisAutoConfiguration.class},
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
class SpringDataRedisTest {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private final List<String> testKeys = new ArrayList<>();

    private String newKey(String type) {
        String key = "sky:test:redis:" + type + ":" + UUID.randomUUID();
        testKeys.add(key);
        return key;
    }

    @AfterEach
    void cleanUp() {
        // 只删除当前测试创建的键，保留业务数据。
        if (!testKeys.isEmpty()) {
            redisTemplate.delete(testKeys);
        }
    }

    @Test
    void testString() {
        String key = newKey("string");

        redisTemplate.opsForValue().set(key, "苍穹外卖", 5, TimeUnit.MINUTES);

        assertEquals("苍穹外卖", redisTemplate.opsForValue().get(key));
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        assertNotNull(ttl);
        assertTrue(ttl > 0 && ttl <= 300);
    }

    @Test
    void testHash() {
        String key = newKey("hash");

        redisTemplate.opsForHash().put(key, "name", "张三");
        redisTemplate.opsForHash().put(key, "age", 20);

        assertEquals("张三", redisTemplate.opsForHash().get(key, "name"));
        assertEquals(20, redisTemplate.opsForHash().get(key, "age"));
        assertEquals(Long.valueOf(2), redisTemplate.opsForHash().size(key));

        assertEquals(Long.valueOf(1), redisTemplate.opsForHash().delete(key, "age"));
        assertFalse(Boolean.TRUE.equals(redisTemplate.opsForHash().hasKey(key, "age")));
    }

    @Test
    void testList() {
        String key = newKey("list");

        redisTemplate.opsForList().rightPushAll(key, "A", "B", "C");

        assertEquals(Arrays.asList("A", "B", "C"), redisTemplate.opsForList().range(key, 0, -1));
        assertEquals("A", redisTemplate.opsForList().leftPop(key));
        assertEquals(Long.valueOf(2), redisTemplate.opsForList().size(key));
    }

    @Test
    void testSet() {
        String key = newKey("set");

        redisTemplate.opsForSet().add(key, "A", "B", "A");

        assertEquals(new HashSet<>(Arrays.asList("A", "B")), redisTemplate.opsForSet().members(key));
        assertEquals(Boolean.TRUE, redisTemplate.opsForSet().isMember(key, "A"));
        assertEquals(Long.valueOf(1), redisTemplate.opsForSet().remove(key, "A"));
        assertEquals(Long.valueOf(1), redisTemplate.opsForSet().size(key));
    }

    @Test
    void testZSet() {
        String key = newKey("zset");

        redisTemplate.opsForZSet().add(key, "宫保鸡丁", 100);
        redisTemplate.opsForZSet().add(key, "鱼香肉丝", 200);

        assertEquals(Double.valueOf(200), redisTemplate.opsForZSet().score(key, "鱼香肉丝"));
        assertEquals(Arrays.asList("鱼香肉丝", "宫保鸡丁"),
                new ArrayList<>(redisTemplate.opsForZSet().reverseRange(key, 0, -1)));
    }

    @Test
    void testKeyOperations() {
        String key = newKey("key");

        assertEquals(Boolean.FALSE, redisTemplate.hasKey(key));
        redisTemplate.opsForValue().set(key, "value");
        assertEquals(Boolean.TRUE, redisTemplate.hasKey(key));
        assertEquals(Boolean.TRUE, redisTemplate.expire(key, 1, TimeUnit.MINUTES));

        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        assertNotNull(ttl);
        assertTrue(ttl > 0 && ttl <= 60);

        assertEquals(Boolean.TRUE, redisTemplate.delete(key));
        assertEquals(Boolean.FALSE, redisTemplate.hasKey(key));
        assertNull(redisTemplate.opsForValue().get(key));
    }
}
