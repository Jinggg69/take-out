package com.junzhou.test;

import com.junzhou.context.RequestScope;
import com.junzhou.entity.StoreSettings;
import com.junzhou.exception.OrderBusinessException;
import com.junzhou.mapper.StoreSettingsMapper;
import com.junzhou.service.StoreSettingsService;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.apache.ibatis.session.SqlSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StoreSettingsContactPhoneTest {
    @Test void contactPhoneIsPersistedWithConfigVersionAndRejectsInvalidOrStaleUpdates() throws Exception {
        JdbcDataSource db = new JdbcDataSource(); db.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(db);
        jdbc.execute("create table tenant(id bigint primary key,status int)");
        jdbc.execute("create table store(id bigint primary key,tenant_id bigint,status int)");
        com.junzhou.test.support.StoreSettingsTestSchema.create(jdbc);
        jdbc.update("insert into tenant(id,status) values(1,1)"); jdbc.update("insert into store(id,tenant_id,status) values(11,1,1)");
        jdbc.update("insert into store_settings(tenant_id,store_id,address,latitude,longitude) values(1,11,'地址',32,111)");
        SqlSessionFactoryBean bean = new SqlSessionFactoryBean(); bean.setDataSource(db);
        org.apache.ibatis.session.Configuration config = new org.apache.ibatis.session.Configuration();
        config.setMapUnderscoreToCamelCase(true); config.addMapper(StoreSettingsMapper.class); bean.setConfiguration(config);
        RequestScope.set(new RequestScope(42L,null,1L,11L,Collections.singleton(11L)));
        try(SqlSession session = bean.getObject().openSession(true)) {
            StoreSettingsMapper mapper = session.getMapper(StoreSettingsMapper.class);
            StoreSettingsService service = new StoreSettingsService(); ReflectionTestUtils.setField(service,"mapper",mapper);
            RedisTemplate redis = mock(RedisTemplate.class,RETURNS_DEEP_STUBS); when(redis.opsForValue().get(anyString())).thenReturn(null);
            ReflectionTestUtils.setField(service,"redisTemplate",redis);
            StoreSettings settings = payload(service.current()); settings.setContactPhone(" 0719-1234567 "); service.save(settings);
            assertEquals("0719-1234567",service.current().getContactPhone()); assertEquals(1L,service.current().getVersion());
            StoreSettings stale = payload(service.current()); stale.setVersion(0L); stale.setContactPhone("123456");
            assertThrows(OrderBusinessException.class,()->service.save(stale));
            assertEquals("0719-1234567",service.current().getContactPhone());
            for(String invalid:Arrays.asList("abc<script>",String.join("",Collections.nCopies(33,"1")))) {
                StoreSettings bad = payload(service.current()); bad.setContactPhone(invalid);
                assertThrows(OrderBusinessException.class,()->service.save(bad));
                assertEquals(1L,service.current().getVersion());
            }
            StoreSettings blank = payload(service.current()); blank.setContactPhone("  "); service.save(blank);
            assertNull(service.current().getContactPhone()); assertEquals(2L,service.current().getVersion());
        } finally { RequestScope.clear(); }
    }
    // An HTTP update body is detached from the mapper's session-cached read entity.
    private StoreSettings payload(StoreSettings read) {
        StoreSettings body = new StoreSettings();
        org.springframework.beans.BeanUtils.copyProperties(read,body);
        return body;
    }
}
