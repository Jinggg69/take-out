package com.junzhou.test;

import com.junzhou.context.RequestScope;
import com.junzhou.mapper.UserMapper;
import org.apache.ibatis.session.SqlSession;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.Collections;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** SQL authorization of global customer identities and payment-only order lookups. */
class UserMapperIsolationTest {
    @Test void customersReadOnlySelfAndPaymentLookupRequiresAuthorizedOrder() throws Exception {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1");
        JdbcTemplate db = new JdbcTemplate(source);
        db.execute("create table `user` (id bigint primary key,openid varchar(40),name varchar(40))");
        db.execute("create table store(id bigint primary key,tenant_id bigint)");
        db.update("insert into store values(1,1),(2,2),(3,1)");
        db.execute("create table orders (id bigint primary key,user_id bigint,tenant_id bigint,store_id bigint)");
        db.update("insert into `user` values(42,'wx42','private42'),(84,'wx84','private84')");
        db.update("insert into orders values(1,42,1,1),(2,84,1,1),(3,42,2,2),(4,42,1,3)");
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean(); factory.setDataSource(source);
        org.apache.ibatis.session.SqlSessionFactory f = factory.getObject();
        f.getConfiguration().addMapper(UserMapper.class);
        try (SqlSession session = f.openSession(true)) {
            UserMapper mapper = session.getMapper(UserMapper.class);
            RequestScope.set(new RequestScope(null,42L,1L,1L,Collections.emptySet()));
            assertEquals(42L,mapper.getById(42L).getId());
            assertNull(mapper.getById(84L)); assertNull(mapper.lockById(84L));
            assertEquals(42L,mapper.lockById(42L));
            assertNotNull(mapper.getPaymentUserByOrderId(1L));
            assertNull(mapper.getPaymentUserByOrderId(2L));
            assertNull(mapper.getPaymentUserByOrderId(3L));
            assertNull(mapper.getPaymentUserByOrderId(4L));
            RequestScope.set(new RequestScope(7L,null,1L,1L,Collections.singleton(1L)));
            assertThrows(RuntimeException.class,()->mapper.getById(42L));
            assertThrows(RuntimeException.class,()->mapper.lockById(42L));
            assertNull(mapper.getPaymentUserByOrderId(3L));
            assertNull(mapper.getPaymentUserByOrderId(2L).getName());
            assertEquals("wx84",mapper.getPaymentUserByOrderId(2L).getOpenid());
            db.update("update store set tenant_id=2 where id=1");
            session.clearCache();
            assertNull(mapper.getPaymentUserByOrderId(2L));
            RequestScope.clear();
            assertThrows(RuntimeException.class,()->mapper.getById(42L));
            assertThrows(RuntimeException.class,()->mapper.getPaymentUserByOrderId(1L));
            assertEquals(42L,mapper.getByOpenid("wx42").getId());
        } finally { RequestScope.clear(); db.execute("SET DB_CLOSE_DELAY 0"); }
    }
}
