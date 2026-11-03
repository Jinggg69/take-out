package com.junzhou.test;

import com.junzhou.context.RequestScope;
import com.junzhou.entity.Setmeal;
import com.junzhou.dto.SetmealDTO;
import com.junzhou.dto.DishDTO;
import com.junzhou.service.DishService;
import com.junzhou.service.SetmealService;
import com.junzhou.vo.DishVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real controllers against a shared Redis boundary, including cache hits. */
class CacheIsolationTest {
    private final Map<String, Object> data = new HashMap<>();
    private final RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
    private final ValueOperations<String, Object> values = mock(ValueOperations.class);

    CacheIsolationTest() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenAnswer(i -> data.get(i.getArgument(0)));
        doAnswer(i -> { data.put(i.getArgument(0), i.getArgument(1)); return null; })
                .when(values).set(anyString(), any());
        when(redis.scan(any(ScanOptions.class))).thenAnswer(i -> {
            String pattern = ((ScanOptions) i.getArgument(0)).getPattern();
            Iterator<String> keys = new ArrayList<>(data.keySet()).stream()
                    .filter(k -> k.startsWith(pattern.substring(0, pattern.length() - 1))).iterator();
            Cursor<String> cursor = mock(Cursor.class);
            when(cursor.hasNext()).thenAnswer(c -> keys.hasNext());
            when(cursor.next()).thenAnswer(c -> keys.next());
            return cursor;
        });
        when(redis.delete(anyString())).thenAnswer(i -> data.remove(i.getArgument(0)) != null);
    }

    @AfterEach void clear() { RequestScope.clear(); }
    @Configuration @EnableCaching static class CachingConfig { }
    private void scope(long tenant, long store) {
        RequestScope.set(new RequestScope(null, null, tenant, store, Collections.emptySet()));
    }

    @Test void dishCacheCannotReturnAnotherTenantsCategory() {
        com.junzhou.controller.user.DishController controller = new com.junzhou.controller.user.DishController();
        DishService service = mock(DishService.class);
        ReflectionTestUtils.setField(controller, "redisTemplate", redis);
        ReflectionTestUtils.setField(controller, "dishService", service);
        when(service.listWithFlavor(any())).thenAnswer(i -> Collections.singletonList(
                DishVO.builder().id(RequestScope.requireTenantId()).build()));
        scope(101, 1001);
        assertEquals(101L, controller.list(7L).getData().get(0).getId());
        scope(102, 2001);
        assertEquals(102L, controller.list(7L).getData().get(0).getId());
        scope(101, 1002);
        assertEquals(101L, controller.list(7L).getData().get(0).getId());
        verify(service, times(2)).listWithFlavor(any());
    }

    @Test void dishCacheHitStillRequiresAnAuthorisedStore() {
        com.junzhou.controller.user.DishController controller = new com.junzhou.controller.user.DishController();
        ReflectionTestUtils.setField(controller, "redisTemplate", redis);
        data.put("dish_7", Collections.singletonList(DishVO.builder().id(99L).build()));
        assertThrows(SecurityException.class, () -> controller.list(7L));
        RequestScope.set(new RequestScope(42L, null, 101L, 1002L, Collections.singleton(1001L)));
        assertThrows(SecurityException.class, () -> controller.list(7L));
    }

    @Test void dishMutationsEvictAllTenantCategoriesButPreserveOtherTenantsAndResources() {
        com.junzhou.controller.user.DishController user = new com.junzhou.controller.user.DishController();
        com.junzhou.controller.admin.DishController admin = new com.junzhou.controller.admin.DishController();
        DishService service = mock(DishService.class);
        ReflectionTestUtils.setField(user, "redisTemplate", redis);
        ReflectionTestUtils.setField(user, "dishService", service);
        ReflectionTestUtils.setField(admin, "redisTemplate", redis);
        ReflectionTestUtils.setField(admin, "dishService", service);
        when(service.listWithFlavor(any())).thenAnswer(i -> Collections.singletonList(
                DishVO.builder().name("fresh").build()));
        DishDTO dto = new DishDTO(); dto.setCategoryId(8L);
        List<Runnable> mutations = Arrays.asList(() -> admin.save(dto), () -> admin.update(dto),
                () -> admin.startOrStop(0, 1L), () -> admin.delete(Collections.singletonList(1L)));
        data.put("junzhou:v2:tenant:1010:dish:category:7", "other tenant");
        data.put("junzhou:v2:tenant:101:setmeal:category:7", "setmeal");
        data.put("junzhou:v2:tenant:101:store:1001:shop-status", 0);
        for (Runnable mutation : mutations) {
            scope(101, 1001);
            List<DishVO> old = Collections.singletonList(DishVO.builder().name("old").build());
            data.put("junzhou:v2:tenant:101:dish:category:7", old);
            data.put("junzhou:v2:tenant:101:dish:category:all", old);
            mutation.run();
            scope(101, 1002);
            assertEquals("fresh", user.list(7L).getData().get(0).getName());
            assertEquals("fresh", user.list(null).getData().get(0).getName());
            assertEquals("other tenant", data.get("junzhou:v2:tenant:1010:dish:category:7"));
            assertEquals("setmeal", data.get("junzhou:v2:tenant:101:setmeal:category:7"));
            assertEquals(0, data.get("junzhou:v2:tenant:101:store:1001:shop-status"));
        }
        RequestScope.clear();
        assertThrows(SecurityException.class, () -> admin.update(dto));
    }

    @Test void failedDishWritePreservesCachedData() {
        com.junzhou.controller.admin.DishController admin = new com.junzhou.controller.admin.DishController();
        DishService service = mock(DishService.class);
        ReflectionTestUtils.setField(admin, "redisTemplate", redis);
        ReflectionTestUtils.setField(admin, "dishService", service);
        doThrow(new SecurityException("foreign dish")).when(service).deleteBatch(anyList());
        scope(101, 1001);
        data.put("junzhou:v2:tenant:101:dish:category:7", "unchanged");
        assertThrows(SecurityException.class, () -> admin.delete(Collections.singletonList(1L)));
        assertEquals("unchanged", data.get("junzhou:v2:tenant:101:dish:category:7"));
        verifyNoInteractions(redis);
    }

    @Test void shopStatusIsIsolatedByTenantAndStoreAndIgnoresLegacyKey() {
        com.junzhou.controller.admin.ShopController admin = new com.junzhou.controller.admin.ShopController();
        com.junzhou.controller.user.ShopController user = new com.junzhou.controller.user.ShopController();
        ReflectionTestUtils.setField(admin, "redisTemplate", redis);
        ReflectionTestUtils.setField(user, "redisTemplate", redis);
        data.put("SHOP_STATUS", 0);
        scope(101, 1001);
        assertEquals(1, user.getStatus().getData());
        admin.setStatus(0);
        assertEquals(0, user.getStatus().getData());
        scope(101, 1002);
        assertEquals(1, user.getStatus().getData());
        scope(102, 1001);
        assertEquals(1, user.getStatus().getData());
        RequestScope.clear();
        assertThrows(SecurityException.class, user::getStatus);
        assertThrows(SecurityException.class, () -> admin.setStatus(0));
    }

    @Test void setmealHitsAndAllMutationsStayWithinTenant() {
        SetmealService service = mock(SetmealService.class);
        Map<Long, String> names = new HashMap<>();
        names.put(101L, "old"); names.put(102L, "other");
        when(service.list(any())).thenAnswer(i -> Collections.singletonList(Setmeal.builder()
                .name(names.get(RequestScope.requireTenantId())).build()));
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(CachingConfig.class, com.junzhou.controller.user.SetmealController.class,
                    com.junzhou.controller.admin.SetmealController.class);
            context.registerBean("cacheManager", ConcurrentMapCacheManager.class, () -> new ConcurrentMapCacheManager("setmealCache"));
            context.registerBean("redisTemplate", RedisTemplate.class, () -> redis);
            context.registerBean(SetmealService.class, () -> service);
            context.refresh();
            com.junzhou.controller.user.SetmealController user = context.getBean(com.junzhou.controller.user.SetmealController.class);
            com.junzhou.controller.admin.SetmealController admin = context.getBean(com.junzhou.controller.admin.SetmealController.class);
            scope(101, 1001);
            assertEquals("old", user.list(7L).getData().get(0).getName());
            scope(102, 2001);
            assertEquals("other", user.list(7L).getData().get(0).getName());
            scope(101, 1002);
            assertEquals("old", user.list(7L).getData().get(0).getName());
            verify(service, times(2)).list(any());
            SetmealDTO dto = new SetmealDTO(); dto.setCategoryId(8L);
            List<Runnable> mutations = Arrays.asList(() -> admin.save(dto), () -> admin.update(dto),
                    () -> admin.startOrStop(0, 1L), () -> admin.delete(Collections.singletonList(1L)));
            int calls = 2;
            for (Runnable mutation : mutations) {
                names.put(101L, "new" + calls);
                mutation.run();
                assertEquals("new" + calls, user.list(7L).getData().get(0).getName());
                scope(102, 2001);
                assertEquals("other", user.list(7L).getData().get(0).getName());
                verify(service, times(++calls)).list(any());
                scope(101, 1001);
            }
            RequestScope.clear();
            assertThrows(SecurityException.class, () -> user.list(7L));
        }
    }
}
