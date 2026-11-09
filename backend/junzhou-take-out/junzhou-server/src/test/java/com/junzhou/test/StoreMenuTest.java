package com.junzhou.test;

import com.junzhou.context.RequestScope;
import com.junzhou.entity.*;
import com.junzhou.exception.OrderBusinessException;
import com.junzhou.mapper.*;
import com.junzhou.service.StoreMenuService;
import org.apache.ibatis.session.SqlSession;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StoreMenuTest {
    @AfterEach void clear() { RequestScope.clear(); }
    private void scope(long tenant, long store) {
        RequestScope.set(new RequestScope(1L, null, tenant, store, Collections.singleton(store)));
    }

    @Test void menuSqlScopesCatalogAndPricesAndUpsertsOnlyOwnedProducts() throws Exception {
        JdbcDataSource db = new JdbcDataSource();
        db.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(db);
        jdbc.execute("CREATE TABLE tenant(id BIGINT PRIMARY KEY,status INT)");
        jdbc.execute("CREATE TABLE store(id BIGINT PRIMARY KEY,tenant_id BIGINT,status INT)");
        for (String table : Arrays.asList("dish", "setmeal")) {
            jdbc.execute("CREATE TABLE " + table + "(id BIGINT PRIMARY KEY,tenant_id BIGINT,name VARCHAR(40),price DECIMAL(10,2),category_id BIGINT,image VARCHAR(40),description VARCHAR(40),status INT,update_time TIMESTAMP)");
            String relation = "dish".equals(table) ? "dish_id" : "setmeal_id";
            jdbc.execute("CREATE TABLE store_" + table + "(tenant_id BIGINT,store_id BIGINT," + relation + " BIGINT,on_sale INT,sold_out INT,override_price DECIMAL(10,2),sort INT,UNIQUE(tenant_id,store_id," + relation + "))");
            jdbc.update("INSERT INTO " + table + "(id,tenant_id,name,price,category_id,status) VALUES(1,1,'one',10,7,1),(2,1,'two',20,7,1),(3,2,'foreign',30,7,1),(4,1,'disabled',40,7,0)");
        }
        jdbc.update("INSERT INTO tenant VALUES(1,1),(2,1)");
        jdbc.update("INSERT INTO store VALUES(11,1,1),(12,1,1),(21,2,1)");
        SqlSessionFactoryBean bean = new SqlSessionFactoryBean(); bean.setDataSource(db);
        org.apache.ibatis.session.Configuration config = new org.apache.ibatis.session.Configuration();
        config.setMapUnderscoreToCamelCase(true);
        // Fixture changes use JdbcTemplate outside this session; do not reuse its first-level query results.
        config.setLocalCacheScope(org.apache.ibatis.session.LocalCacheScope.STATEMENT); bean.setConfiguration(config);
        bean.setMapperLocations(new ClassPathResource("mapper/StoreMenuMapper.xml"));
        try (SqlSession session = bean.getObject().openSession(true)) {
            StoreMenuMapper mapper = session.getMapper(StoreMenuMapper.class);
            scope(1,11);
            assertEquals(3, mapper.dishes().size());
            assertEquals(0, mapper.dishes().get(0).getOnSale());
            assertTrue(mapper.publicDishes(7L).isEmpty());
            StoreMenuItem item = item(1L); item.setOverridePrice(new BigDecimal("9.50")); item.setSort(10);
            assertEquals(11L, mapper.lockStore()); mapper.saveDish(item); mapper.saveSetmeal(item);
            item.setId(2L); item.setSort(1); item.setOverridePrice(null); mapper.saveDish(item);
            assertEquals(Arrays.asList(2L,1L), Arrays.asList(mapper.publicDishes(7L).get(0).getId(), mapper.publicDishes(7L).get(1).getId()));
            assertEquals(new BigDecimal("9.50"), mapper.publicDishes(7L).get(1).getPrice());
            assertEquals(new BigDecimal("9.50"), mapper.publicSetmeals(7L).get(0).getPrice());
            assertTrue(mapper.publicDishes(8L).isEmpty());
            item.setId(3L); assertEquals(0, mapper.saveDish(item)); assertEquals(0, mapper.saveSetmeal(item));
            item.setId(4L); mapper.saveDish(item); assertEquals(2, mapper.publicDishes(7L).size());
            item.setId(1L); item.setSoldOut(1); mapper.saveDish(item); assertEquals(1, mapper.publicDishes(7L).size());
            scope(1,12); assertTrue(mapper.publicDishes(null).isEmpty()); assertTrue(mapper.publicSetmeals(null).isEmpty());
            item.setSoldOut(0); item.setOverridePrice(new BigDecimal("12.00")); mapper.saveDish(item);
            assertEquals(new BigDecimal("12.00"), mapper.publicDishes(null).get(0).getPrice());
            jdbc.update("UPDATE store SET status=0 WHERE id=12"); assertTrue(mapper.publicDishes(null).isEmpty());
            scope(2,21); assertEquals(1, mapper.dishes().size()); assertTrue(mapper.publicDishes(null).isEmpty());
            scope(1,11); jdbc.update("UPDATE tenant SET status=0 WHERE id=1"); assertTrue(mapper.publicSetmeals(null).isEmpty());
            RequestScope.clear(); assertThrows(RuntimeException.class, mapper::dishes);
        }
    }

    @Test void validationRejectsInvalidInputForeignIdsAndMissingScopeBeforeWriting() {
        StoreMenuService service = new StoreMenuService(); StoreMenuMapper mapper = mock(StoreMenuMapper.class);
        DishMapper dishes = mock(DishMapper.class);
        ReflectionTestUtils.setField(service,"mapper",mapper); ReflectionTestUtils.setField(service,"dishMapper",dishes);
        assertThrows(SecurityException.class, () -> service.save("dish",item(1L)));
        scope(1,11); when(mapper.lockStore()).thenReturn(11L);
        assertThrows(OrderBusinessException.class, () -> service.save("unknown",item(1L)));
        StoreMenuItem item = item(1L); item.setOnSale(2);
        assertThrows(OrderBusinessException.class, () -> service.save("dish",item)); item.setOnSale(1);
        for (String price : Arrays.asList("-1", "1.001", "100000000")) {
            item.setOverridePrice(new BigDecimal(price));
            assertThrows(OrderBusinessException.class, () -> service.save("dish",item));
        }
        item.setOverridePrice(null);
        assertThrows(OrderBusinessException.class, () -> service.save("dish",item));
        verify(mapper,never()).saveDish(any());
        when(dishes.getById(1L)).thenReturn(new Dish()); service.save("dish",item);
        org.mockito.InOrder order = inOrder(mapper,dishes); order.verify(mapper).lockStore(); order.verify(dishes).getById(1L);
        verify(mapper).saveDish(item);
    }

    @Test void setmealListingRejectsEmptyForeignSoldOutOrMalformedComponents() {
        scope(1,11);
        StoreMenuService service = new StoreMenuService(); StoreMenuMapper mapper = mock(StoreMenuMapper.class);
        SetmealDishMapper components = mock(SetmealDishMapper.class); DishMapper dishes = mock(DishMapper.class);
        ReflectionTestUtils.setField(service,"mapper",mapper); ReflectionTestUtils.setField(service,"setmealDishMapper",components);
        ReflectionTestUtils.setField(service,"dishMapper",dishes);
        when(mapper.publicSetmeals(null)).thenReturn(Arrays.asList(Setmeal.builder().id(1L).build(),Setmeal.builder().id(2L).build(),Setmeal.builder().id(3L).build(),Setmeal.builder().id(4L).build()));
        when(components.countInvalidDishes(1L)).thenReturn(1);
        when(components.getBySetmealId(2L)).thenReturn(Collections.emptyList());
        SetmealDish part = new SetmealDish(); part.setDishId(10L); part.setCopies(1);
        when(components.getBySetmealId(3L)).thenReturn(Collections.singletonList(part));
        when(components.getBySetmealId(4L)).thenReturn(Collections.singletonList(part));
        assertTrue(service.publicSetmeals(null).isEmpty());
        when(dishes.getAvailableForStore(10L)).thenReturn(new Dish());
        assertEquals(2, service.publicSetmeals(null).size());
        part.setCopies(0); assertTrue(service.publicSetmeals(null).isEmpty());
    }

    private StoreMenuItem item(Long id) {
        StoreMenuItem item = new StoreMenuItem(); item.setId(id); item.setOnSale(1); item.setSoldOut(0); item.setSort(0); return item;
    }
}
