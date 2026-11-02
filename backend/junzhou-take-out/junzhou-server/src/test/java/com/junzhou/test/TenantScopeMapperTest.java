package com.junzhou.test;

import com.junzhou.mapper.TenantScopeMapper;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class TenantScopeMapperTest {
    @Test void databaseDeterminesActiveStoreAndFailsClosedForExtraTenantOrStore() throws Exception {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate db = new JdbcTemplate(source);
        db.execute("create table tenant (id bigint primary key,status int)");
        db.execute("create table store (id bigint primary key,tenant_id bigint,status int)");
        db.execute("create table employee (id bigint primary key,tenant_id bigint,status int)");
        db.update("insert into tenant values(1,1)");
        db.update("insert into store values(1,1,1)");
        db.update("insert into employee values(42,1,1)");
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean(); factoryBean.setDataSource(source);
        SqlSessionFactory factory = factoryBean.getObject();
        factory.getConfiguration().addMapper(TenantScopeMapper.class);
        try (SqlSession session = factory.openSession()) {
            TenantScopeMapper m = session.getMapper(TenantScopeMapper.class);
            assertEquals(1, m.isLegacySingleStore());
            assertEquals(1L, m.activeEmployeeTenant(42L));
            assertEquals(1L, m.singleActiveStore(1L));
            assertEquals(1L, m.activeStoreTenant(1L));
            assertNull(m.activeStoreTenant(9L));
            db.update("insert into store values(2,1,1)");
            session.clearCache();
            assertEquals(0, m.isLegacySingleStore());
            assertNull(m.singleActiveStore(1L));
            db.update("delete from store where id=2");
            db.update("update tenant set status=0 where id=1");
            session.clearCache();
            assertEquals(0, m.isLegacySingleStore());
            assertNull(m.activeEmployeeTenant(42L));
            assertNull(m.activeStoreTenant(1L));
        }
    }
}
