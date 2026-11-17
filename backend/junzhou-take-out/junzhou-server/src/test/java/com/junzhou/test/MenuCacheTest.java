package com.junzhou.test;

import com.junzhou.cache.MenuCacheAspect;
import com.junzhou.context.RequestScope;
import com.junzhou.entity.*;
import com.junzhou.mapper.*;
import com.junzhou.service.StoreMenuService;
import org.junit.jupiter.api.*;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MenuCacheTest {
    RedisTemplate<String,Object> redis;
    ValueOperations<String,Object> values;
    StoreMenuMapper mapper;
    StoreMenuService service;
    Map<String,Object> data;
    @BeforeEach @SuppressWarnings("unchecked") void setup() {
        scope(1,11);
        redis=mock(RedisTemplate.class); values=mock(ValueOperations.class); data=new HashMap<>();
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenAnswer(i->data.get(i.getArgument(0)));
        doAnswer(i->{data.put(i.getArgument(0),i.getArgument(1));return null;}).when(values).set(anyString(),any());
        doAnswer(i->{data.put(i.getArgument(0),i.getArgument(1));return null;}).when(values).set(anyString(),any(),anyLong(),any(TimeUnit.class));
        StoreMenuService target=new StoreMenuService(); mapper=mock(StoreMenuMapper.class);
        ReflectionTestUtils.setField(target,"mapper",mapper);
        DishMapper dishes=mock(DishMapper.class); when(dishes.getById(1L)).thenReturn(new Dish());
        ReflectionTestUtils.setField(target,"dishMapper",dishes);
        when(mapper.lockStore()).thenReturn(11L);
        when(mapper.publicDishes(any())).thenReturn(Collections.emptyList());
        when(mapper.publicSetmeals(any())).thenReturn(Collections.emptyList());
        AspectJProxyFactory factory=new AspectJProxyFactory(target);
        factory.addAspect(new MenuCacheAspect(redis)); service=factory.getProxy();
    }
    @AfterEach void clear() { RequestScope.clear(); if(TransactionSynchronizationManager.isSynchronizationActive())TransactionSynchronizationManager.clearSynchronization(); }
    void scope(long tenant,long store){RequestScope.set(new RequestScope(1L,null,tenant,store,Collections.singleton(store)));}
    StoreMenuItem item(){StoreMenuItem i=new StoreMenuItem();i.setId(1L);i.setOnSale(1);i.setSoldOut(0);i.setSort(0);return i;}
    @Test void repeatedReadsCacheEvenEmptyResultsWithFiniteTtl() {
        service.publicDishes(7L);service.publicDishes(7L);
        verify(mapper,times(1)).publicDishes(7L);
        verify(values).set(contains(":store:11:"),any(),eq(180L),eq(TimeUnit.SECONDS));
    }
    @Test void isolatesTenantStoreCategoryAndProductType() {
        service.publicDishes(null); service.publicSetmeals(null); service.publicDishes(7L);
        scope(1,12);service.publicDishes(null);scope(2,11);service.publicDishes(null);
        verify(mapper,times(3)).publicDishes(null);verify(mapper).publicDishes(7L);verify(mapper).publicSetmeals(null);
    }
    @Test void missingScopeCannotReadCachedData() {
        service.publicDishes(null);RequestScope.clear();
        assertThrows(SecurityException.class,()->service.publicDishes(null));
    }
    @Test void redisOutageFallsBackWithoutSwallowingDatabaseErrors() {
        when(values.get(anyString())).thenThrow(new RedisConnectionFailureException("offline"));
        assertTrue(service.publicDishes(null).isEmpty());
        when(mapper.publicDishes(null)).thenThrow(new IllegalStateException("database failed"));
        assertThrows(IllegalStateException.class,()->service.publicDishes(null));
    }
    @Test void mutationInvalidatesAllStoresAndBothMenuTypesButNotOtherTenants() {
        service.publicDishes(null);service.publicSetmeals(null);
        scope(1,12);service.publicDishes(null);scope(2,21);service.publicDishes(null);
        scope(1,11);service.save("dish",item());service.publicDishes(null);service.publicSetmeals(null);
        scope(1,12);service.publicDishes(null);scope(2,21);service.publicDishes(null);
        verify(mapper,times(5)).publicDishes(null);verify(mapper,times(2)).publicSetmeals(null);
    }
    @Test void invalidatesOnlyAfterCommitAndNotOnRollback() {
        service.publicDishes(null);TransactionSynchronizationManager.initSynchronization();
        service.save("dish",item());service.publicDishes(null);verify(mapper).publicDishes(null);
        List<TransactionSynchronization> callbacks=TransactionSynchronizationManager.getSynchronizations();
        assertFalse(callbacks.isEmpty());callbacks.forEach(TransactionSynchronization::afterCommit);
        TransactionSynchronizationManager.clearSynchronization();service.publicDishes(null);
        verify(mapper,times(2)).publicDishes(null);
        TransactionSynchronizationManager.initSynchronization();service.save("dish",item());
        TransactionSynchronizationManager.getSynchronizations().forEach(c->c.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        TransactionSynchronizationManager.clearSynchronization();service.publicDishes(null);
        verify(mapper,times(2)).publicDishes(null);
    }
    @Test void failedMutationDoesNotInvalidate() {
        service.publicDishes(null);when(mapper.lockStore()).thenReturn(null);
        assertThrows(RuntimeException.class,()->service.save("dish",item()));service.publicDishes(null);
        verify(mapper).publicDishes(null);
    }
    @Test void oldInFlightReadCannotRepopulateTheNewGeneration() {
        when(mapper.publicDishes(null)).thenAnswer(i->{service.save("dish",item());return Collections.emptyList();});
        service.publicDishes(null);
        doReturn(Collections.emptyList()).when(mapper).publicDishes(null);
        service.publicDishes(null);service.publicDishes(null);
        verify(mapper,times(2)).publicDishes(null);
    }
    @Test void cacheWriteAndInvalidationFailuresDoNotFailBusinessOperations() {
        doThrow(new RedisConnectionFailureException("offline")).when(values).set(anyString(),any(),anyLong(),any(TimeUnit.class));
        assertTrue(service.publicDishes(null).isEmpty());
        doThrow(new RedisConnectionFailureException("offline")).when(values).set(anyString(),any());
        assertDoesNotThrow(()->service.save("dish",item()));verify(mapper).saveDish(any());
    }
    @Test void catalogDishChangeInvalidatesSetmealListsThroughServiceProxy() {
        service.publicSetmeals(null);
        com.junzhou.service.impl.DishServiceImpl target=spy(new com.junzhou.service.impl.DishServiceImpl());
        doNothing().when(target).startOrStop(0,1L);
        AspectJProxyFactory factory=new AspectJProxyFactory(target);factory.setProxyTargetClass(true);
        factory.addAspect(new MenuCacheAspect(redis));
        ((com.junzhou.service.impl.DishServiceImpl)factory.getProxy()).startOrStop(0,1L);
        service.publicSetmeals(null);verify(mapper,times(2)).publicSetmeals(null);
    }
}
