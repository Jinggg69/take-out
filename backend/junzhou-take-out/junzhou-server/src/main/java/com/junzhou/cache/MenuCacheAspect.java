package com.junzhou.cache;

import com.junzhou.context.RequestScope;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Public menu lists only: checkout pricing and availability always read the database. */
@Aspect @Component @Slf4j
public class MenuCacheAspect {
    private final RedisTemplate<String,Object> redis;
    @Value("${sky.menu-cache.ttl-seconds:180}")
    private long ttlSeconds = 180;

    public MenuCacheAspect(RedisTemplate<String,Object> redis) { this.redis=redis; }

    @Around("execution(* com.junzhou.service.StoreMenuService.publicDishes(..)) || execution(* com.junzhou.service.StoreMenuService.publicSetmeals(..))")
    public Object menu(ProceedingJoinPoint call) throws Throwable {
        Long store = RequestScope.requireStoreId();
        Long tenant = RequestScope.requireTenantId();
        String prefix = prefix(tenant);
        String key;
        try {
            Object version = redis.opsForValue().get(prefix + "version");
            key = prefix + "store:" + store + ":" + call.getSignature().getName()
                    + ":category:" + (call.getArgs()[0] == null ? "all" : call.getArgs()[0])
                    + ":version:" + (version == null ? "initial" : version);
            Object cached = redis.opsForValue().get(key);
            if (cached instanceof java.util.List) return cached;
        } catch (DataAccessException | SerializationException ex) {
            log.warn("菜单缓存读取失败，回查数据库，tenantId={}, storeId={}", tenant, store);
            return call.proceed();
        }
        Object result = call.proceed(); // Database and business failures must propagate.
        try {
            // Version is captured BEFORE loading: an in-flight old read cannot repopulate a new generation.
            redis.opsForValue().set(key, result, Math.max(1, ttlSeconds), TimeUnit.SECONDS);
        } catch (DataAccessException | SerializationException ex) {
            log.warn("菜单缓存写入失败，tenantId={}, storeId={}", tenant, store);
        }
        return result;
    }

    // A catalog dish affects setmeal availability too; invalidate both types across all stores of the tenant.
    @AfterReturning("execution(* com.junzhou.service.StoreMenuService.save(..))"
        + " || execution(* com.junzhou.service.impl.DishServiceImpl.save*(..))"
        + " || execution(* com.junzhou.service.impl.DishServiceImpl.update*(..))"
        + " || execution(* com.junzhou.service.impl.DishServiceImpl.delete*(..))"
        + " || execution(* com.junzhou.service.impl.DishServiceImpl.startOrStop(..))"
        + " || execution(* com.junzhou.service.impl.SetmealServiceImpl.save*(..))"
        + " || execution(* com.junzhou.service.impl.SetmealServiceImpl.update*(..))"
        + " || execution(* com.junzhou.service.impl.SetmealServiceImpl.delete*(..))"
        + " || execution(* com.junzhou.service.impl.SetmealServiceImpl.startOrStop(..))"
        + " || execution(* com.junzhou.service.impl.CategoryServiceImpl.save(..))"
        + " || execution(* com.junzhou.service.impl.CategoryServiceImpl.update(..))"
        + " || execution(* com.junzhou.service.impl.CategoryServiceImpl.deleteById(..))"
        + " || execution(* com.junzhou.service.impl.CategoryServiceImpl.startOrStop(..))")
    public void changed() {
        final Long tenant = RequestScope.requireTenantId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { invalidate(tenant); }
            });
        } else invalidate(tenant);
    }

    private void invalidate(Long tenant) {
        try {
            // Do not expire this small namespace token: reverting to an old generation could revive stale entries.
            redis.opsForValue().set(prefix(tenant) + "version", UUID.randomUUID().toString());
        } catch (DataAccessException | SerializationException ex) {
            // Successful DB writes must stay successful. Existing entries expire within ttlSeconds.
            log.warn("菜单缓存失效失败，等待TTL到期，tenantId={}", tenant);
        }
    }

    private static String prefix(Long tenant) { return "junzhou:v3:tenant:" + tenant + ":menu:"; }
}
