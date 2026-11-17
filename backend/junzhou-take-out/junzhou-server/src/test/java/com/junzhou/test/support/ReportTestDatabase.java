package com.junzhou.test.support;

import com.junzhou.mapper.ReportMapper;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.session.SqlSession;
import org.h2.jdbcx.JdbcDataSource;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

/**
 * In-memory MySQL-mode schema with the report fixtures used by the cache tests.
 *
 * <p>Financial fixtures live on tenant 1 stores 1 and 2; workspace fixtures live on tenant 1 store 10
 * on {@link ReportTestSupport#TODAY}. A foreign tenant 2 row proves isolation.</p>
 */
public final class ReportTestDatabase implements AutoCloseable {
    private final JdbcDataSource dataSource;
    private final JdbcTemplate db;
    private final SqlSession session;

    private ReportTestDatabase() throws Exception {
        this.dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        this.db = new JdbcTemplate(dataSource);
        createSchema();
        seed();
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setMapperLocations(new ClassPathResource("mapper/ReportMapper.xml"));
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
        configuration.setLocalCacheScope(LocalCacheScope.STATEMENT);
        factory.setConfiguration(configuration);
        this.session = factory.getObject().openSession();
    }

    public static ReportTestDatabase create() throws Exception {
        return new ReportTestDatabase();
    }

    private void createSchema() {
        db.execute("create table orders(id bigint,tenant_id bigint,store_id bigint,number varchar,"
                + "order_time timestamp,checkout_time timestamp,delivery_time timestamp,cancel_time timestamp,"
                + "amount decimal(10,2),status int,user_id bigint,fulfillment_type varchar)");
        db.execute("create table refund_request(order_id bigint,status varchar,updated_at timestamp,amount_fen bigint)");
        db.execute("create table order_detail(id bigint,order_id bigint,dish_id bigint,setmeal_id bigint,"
                + "name varchar,number int,amount decimal(10,2))");
        for (String kind : new String[]{"dish", "setmeal"}) {
            db.execute("create table " + kind + "(id bigint,tenant_id bigint,name varchar(32),status int)");
            db.execute("create table store_" + kind + "(tenant_id bigint,store_id bigint," + kind
                    + "_id bigint,on_sale int,sold_out int)");
        }
    }

    private void seed() {
        db.update("insert into orders values"
                // Historical report scope: tenant 1 stores 1 and 2.
                + "(1,1,1,'a','2026-11-01 23:00','2026-11-02 00:00','2026-11-02 10:00',null,10,5,1,'DELIVERY'),"
                + "(2,1,1,'b','2026-11-01 21:00','2026-11-02 12:00',null,'2026-11-01 22:00',20,6,2,'PICKUP'),"
                + "(3,1,2,'c','2026-11-02 10:00','2026-11-02 11:00',null,null,30,3,3,'DINE_IN'),"
                // Foreign tenant must never leak into tenant 1 aggregates.
                + "(4,2,3,'d','2026-11-02 10:00','2026-11-02 11:00',null,null,999,3,4,'DELIVERY'),"
                // Workspace fixtures for tenant 1 store 10 on TODAY (2026-11-17).
                + "(101,1,10,'w1','2026-11-17 09:00','2026-11-17 09:05','2026-11-17 09:30',null,100,5,1,'DELIVERY'),"
                + "(102,1,10,'w2','2026-11-17 10:00','2026-11-17 10:10',null,null,50,3,2,'PICKUP'),"
                + "(103,1,10,'w3','2026-11-17 11:00',null,null,'2026-11-17 11:30',30,6,3,'DELIVERY'),"
                + "(104,1,10,'w4','2026-11-17 12:00','2026-11-17 12:05',null,null,20,2,4,'DINE_IN')");
        db.update("insert into refund_request values"
                + "(1,'SUCCESS','2026-11-03 00:00',1000),"
                + "(2,'PROCESSING','2026-11-02 13:00',2000),"
                + "(102,'SUCCESS','2026-11-17 13:00',1000)");
        db.update("insert into order_detail values"
                + "(1,1,1,null,'fish',2,5),"
                + "(2,3,null,1,'meal',1,30),"
                + "(3,2,2,null,'late',1,20),"
                + "(4,101,1,null,'fish',2,100),"
                + "(5,102,null,1,'meal',1,50)");
        db.update("insert into dish values(1,1,'on-a',1),(2,1,'on-b',1),(3,1,'off',1),"
                + "(4,1,'sold-out',1),(5,1,'disabled',0),(6,1,'other-store',1),(7,2,'foreign',1)");
        db.update("insert into store_dish values(1,10,1,1,0),(1,10,2,1,0),(1,10,3,0,0),"
                + "(1,10,4,1,1),(1,10,5,1,0),(1,11,6,1,0),(2,20,7,1,0)");
        db.update("insert into setmeal values(1,1,'meal-on',1),(2,1,'meal-off',1),"
                + "(3,1,'other-store',1),(4,2,'foreign',1)");
        db.update("insert into store_setmeal values(1,10,1,1,0),(1,10,2,0,0),"
                + "(1,11,3,1,0),(2,20,4,1,0)");
    }

    public ReportMapper mapper() {
        return session.getMapper(ReportMapper.class);
    }

    public JdbcTemplate jdbc() {
        return db;
    }

    public SqlSession session() {
        return session;
    }

    @Override
    public void close() {
        if (session != null) session.close();
    }
}
