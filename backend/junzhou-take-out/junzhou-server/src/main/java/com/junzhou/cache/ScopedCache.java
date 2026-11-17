package com.junzhou.cache;

import com.junzhou.context.RequestScope;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.dao.DataAccessException;
import lombok.extern.slf4j.Slf4j;

/** Keys derive only from trusted scope. Catalogs are tenant owned; shop status is store owned. */
@Slf4j
public final class ScopedCache {
    private ScopedCache() { }

    private static String tenantPrefix() {
        RequestScope.requireStoreId(); // Validate store access even on a tenant-wide cache hit.
        return "junzhou:v2:tenant:" + RequestScope.requireTenantId() + ":";
    }

    public static String dishKey(Long categoryId) {
        return tenantPrefix() + "dish:category:" + (categoryId == null ? "all" : categoryId);
    }

    public static String setmealKey(Long categoryId) {
        return tenantPrefix() + "setmeal:category:" + (categoryId == null ? "all" : categoryId);
    }

    public static String shopStatusKey() {
        return tenantPrefix() + "store:" + RequestScope.requireStoreId() + ":shop-status";
    }

    public static void evictDishes(RedisTemplate<String, Object> redis) {
        evict(redis, tenantPrefix() + "dish:");
    }

    public static void evictSetmeals(RedisTemplate<String, Object> redis) {
        evict(redis, tenantPrefix() + "setmeal:");
    }

    private static void evict(RedisTemplate<String, Object> redis, String prefix) {
        // SCAN avoids blocking Redis with KEYS; the delimiter prevents tenant 1 matching tenant 10.
        try (Cursor<String> keys = redis.scan(ScanOptions.scanOptions().match(prefix + "*").count(100).build())) {
            while (keys.hasNext()) redis.delete(keys.next());
        } catch (DataAccessException ex) {
            // Legacy v2 cleanup remains for rolling upgrades; v3 menus use commit-time version invalidation.
            log.warn("旧版商品缓存清理失败");
        }
    }
}
