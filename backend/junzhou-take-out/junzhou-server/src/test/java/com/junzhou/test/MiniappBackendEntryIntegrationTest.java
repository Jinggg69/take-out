package com.junzhou.test;

import com.junzhou.config.WebMvcConfiguration;
import com.junzhou.context.*;
import com.junzhou.controller.user.*;
import com.junzhou.handler.GlobalExceptionHandler;
import com.junzhou.interceptor.*;
import com.junzhou.mapper.*;
import com.junzhou.properties.JwtProperties;
import com.junzhou.service.*;
import com.junzhou.service.impl.*;
import com.junzhou.utils.JwtUtil;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.bind.annotation.*;
import javax.sql.DataSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real MVC registrations, auth/scope interceptors, services and SQL; no legacy scope stub. */
class MiniappBackendEntryIntegrationTest {
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private JdbcTemplate db;
    private static final String SECRET="miniapp-integration-user-secret";
    private static final String QR="0123456789abcdef0123456789abcdef";
    @BeforeEach void setup() {
        context=new AnnotationConfigWebApplicationContext(); context.setServletContext(new MockServletContext());
        context.register(WebMvcConfiguration.class,Config.class); context.refresh();
        db=new JdbcTemplate(context.getBean(DataSource.class));
        db.execute("create table tenant(id bigint primary key,name varchar(100),status int)");
        db.execute("create table store(id bigint primary key,tenant_id bigint,name varchar(100),status int)");
        db.execute("create table `user`(id bigint primary key,name varchar(100),avatar varchar(255))");
        db.execute("create table address_book(id bigint auto_increment primary key,user_id bigint,consignee varchar(50),sex varchar(2),phone varchar(11),province_code varchar(12),province_name varchar(32),city_code varchar(12),city_name varchar(32),district_code varchar(12),district_name varchar(32),detail varchar(200),label varchar(100),is_default int)");
        db.execute("create table store_dining_table(id bigint primary key,tenant_id bigint,store_id bigint,name varchar(64),enabled int,qr_token varchar(32),current_session_id varchar(32))");
        db.execute("create table dining_session(id varchar(32) primary key,tenant_id bigint,store_id bigint,table_id bigint,table_snapshot varchar(64),opened_at timestamp,closed_at timestamp)");
        db.execute("create table dining_session_member(session_id varchar(32),user_id bigint,primary key(session_id,user_id))");
        db.update("insert into tenant values(1,'商家甲',1),(2,'商家乙',1)");
        db.update("insert into store values(11,1,'甲一店',1),(12,1,'甲二店',1),(22,2,'乙店',1)");
        db.update("insert into `user` values(42,'用户甲',null),(84,'用户乙',null)");
        db.update("insert into address_book(id,user_id,consignee,phone,detail,is_default) values(101,42,'甲','13800000001','甲地址',1),(201,84,'乙','13800000002','乙地址',1)");
        db.update("insert into store_dining_table values(7,2,22,'乙桌',1,?,null)",QR);
        mvc=MockMvcBuilders.webAppContextSetup(context).build();
    }
    @AfterEach void close(){RequestScope.clear();BaseContext.removeCurrentId();context.close();}
    private String token(long user){return JwtUtil.createJWT(SECRET,60000,new HashMap<>(Collections.singletonMap("userId",user)));}

    @Test void realMvcProfileAndSharedAddressesWorkWithoutStoreButRejectOtherUsers() throws Exception {
        mvc.perform(get("/user/me").header("authentication",token(42)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.userId").value("42"));
        mvc.perform(get("/user/addressBook/list").header("authentication",token(42)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1)).andExpect(jsonPath("$.data[0].id").value(101));
        mvc.perform(get("/user/addressBook/list").header("authentication",token(42)).header("X-Store-Id","22"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(101));
        mvc.perform(delete("/user/addressBook").param("id","201").header("authentication",token(42)))
            .andExpect(jsonPath("$.code").value(0));
        assertEquals(1,db.queryForObject("select count(*) from address_book where id=201",Integer.class));
        mvc.perform(get("/user/me")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(0));
        assertNull(RequestScope.current()); assertNull(BaseContext.getCurrentId());
    }
    @Test void resolveWithoutSelectionThenJoinRequiresResolvedStoreAndExposesOnlySafeFields() throws Exception {
        mvc.perform(post("/user/dining/resolve").header("authentication",token(42)).contentType("application/json").content("{\"token\":\""+QR+"\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(7)).andExpect(jsonPath("$.data.name").value("乙桌"))
            .andExpect(jsonPath("$.data.merchantName").value("商家乙")).andExpect(jsonPath("$.data.currentSessionId").doesNotExist())
            .andExpect(jsonPath("$.data.qrToken").doesNotExist());
        mvc.perform(post("/user/dining/join").header("authentication",token(42)).contentType("application/json").content("{\"token\":\""+QR+"\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/user/dining/join").header("authentication",token(42)).header("X-Store-Id","11").contentType("application/json").content("{\"token\":\""+QR+"\"}"))
            .andExpect(jsonPath("$.code").value(0));
        mvc.perform(post("/user/dining/join").header("authentication",token(42)).header("X-Store-Id","22").contentType("application/json").content("{\"token\":\""+QR+"\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.currentSessionId").isNotEmpty()).andExpect(jsonPath("$.data.qrToken").doesNotExist());
        assertEquals(1,db.queryForObject("select count(*) from dining_session_member where user_id=42",Integer.class));
        assertNull(RequestScope.current()); assertNull(BaseContext.getCurrentId());
    }
    @Test void revokedCodeAndAnonymousResolveCannotDiscoverTables() throws Exception {
        mvc.perform(post("/user/dining/resolve").contentType("application/json").content("{\"token\":\""+QR+"\"}"))
            .andExpect(status().isUnauthorized());
        db.update("update store_dining_table set enabled=0 where id=7");
        mvc.perform(post("/user/dining/resolve").header("authentication",token(42)).contentType("application/json").content("{\"token\":\""+QR+"\"}"))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(0));
    }
    @Configuration @EnableTransactionManagement static class Config {
        @Bean DataSource dataSource(){JdbcDataSource ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1");return ds;}
        @Bean SqlSessionFactory factory(DataSource ds) throws Exception {
            SqlSessionFactoryBean f=new SqlSessionFactoryBean();f.setDataSource(ds);
            org.apache.ibatis.session.Configuration c=new org.apache.ibatis.session.Configuration();c.setMapUnderscoreToCamelCase(true);
            c.getTypeAliasRegistry().registerAlias("AddressBook",com.junzhou.entity.AddressBook.class);
            c.addMapper(UserMapper.class);c.addMapper(AddressBookMapper.class);c.addMapper(DiningMapper.class);c.addMapper(TenantScopeMapper.class);
            f.setConfiguration(c);f.setMapperLocations(new ClassPathResource("mapper/AddressBookMapper.xml"));return f.getObject();
        }
        @Bean SqlSessionTemplate sql(SqlSessionFactory f){return new SqlSessionTemplate(f);}
        @Bean UserMapper users(SqlSessionTemplate sql){return sql.getMapper(UserMapper.class);}
        @Bean AddressBookMapper addresses(SqlSessionTemplate sql){return sql.getMapper(AddressBookMapper.class);}
        @Bean DiningMapper tables(SqlSessionTemplate sql){return sql.getMapper(DiningMapper.class);}
        @Bean TenantScopeMapper tenants(SqlSessionTemplate sql){return sql.getMapper(TenantScopeMapper.class);}
        @Bean TenantScopeService scopes(TenantScopeMapper mapper){return new TenantScopeServiceImpl(mapper);}
        @Bean RbacService rbacService(){return mock(RbacService.class);}
        @Bean RbacInterceptor rbac(){return mock(RbacInterceptor.class);}
        @Bean JwtTokenAdminInterceptor admin(){return mock(JwtTokenAdminInterceptor.class);}
        @Bean AdminSessionService adminSessionService(){return mock(AdminSessionService.class);}
        @Bean JwtTokenUserInterceptor auth(){return new JwtTokenUserInterceptor();}
        @Bean RequestScopeInterceptor scope(TenantScopeService scopes){return new RequestScopeInterceptor(scopes);}
        @Bean JwtProperties jwt(){JwtProperties p=new JwtProperties();p.setUserSecretKey(SECRET);p.setUserTokenName("authentication");return p;}
        @Bean AddressBookService addressesService(){return new AddressBookServiceImpl();}
        @Bean AddressBookController addressController(){return new AddressBookController();}
        @Bean UserProfileController profile(UserMapper users){return new UserProfileController(users);}
        @Bean DiningService dining(DiningMapper tables){return new DiningServiceImpl(tables,mock(OrderMapper.class));}
        @Bean DiningController diningController(DiningService service){return new DiningController(service);}
        @Bean GlobalExceptionHandler errors(){return new GlobalExceptionHandler();}
        @Bean DataSourceTransactionManager tx(DataSource ds){return new DataSourceTransactionManager(ds);}
    }
}
