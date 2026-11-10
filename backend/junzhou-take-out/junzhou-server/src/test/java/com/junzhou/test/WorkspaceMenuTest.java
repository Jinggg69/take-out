package com.junzhou.test;

import com.junzhou.context.RequestScope;
import com.junzhou.controller.admin.WorkspaceController;
import com.junzhou.interceptor.RbacInterceptor;
import com.junzhou.mapper.ReportMapper;
import com.junzhou.security.PermissionAction;
import com.junzhou.service.RbacService;
import com.junzhou.service.impl.ReportServiceImpl;
import org.apache.ibatis.session.SqlSession;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** HTTP contracts through the real report service and Mapper; MySQL mode is covered by the Python suite. */
class WorkspaceMenuTest {
    private SqlSession session;
    private MockMvc mvc;
    private RbacService rbac;

    @BeforeEach
    void setup() throws Exception {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate db = new JdbcTemplate(ds);
        for (String kind : new String[]{"dish", "setmeal"}) {
            db.execute("create table " + kind + "(id bigint,tenant_id bigint,name varchar(32),status int)");
            db.execute("create table store_" + kind + "(tenant_id bigint,store_id bigint," + kind
                    + "_id bigint,on_sale int,sold_out int)");
        }
        db.update("insert into dish values(1,1,'on-a',1),(2,1,'on-b',1),(3,1,'off',1),"
                + "(4,1,'sold-out',1),(5,1,'disabled',0),(6,1,'other-store',1),(7,2,'foreign',1)");
        db.update("insert into store_dish values(1,10,1,1,0),(1,10,2,1,0),(1,10,3,0,0),"
                + "(1,10,4,1,1),(1,10,5,1,0),(1,11,6,1,0),(2,20,7,1,0)");
        db.update("insert into setmeal values(1,1,'meal-on',1),(2,1,'meal-off',1),"
                + "(3,1,'other-store',1),(4,2,'foreign',1)");
        db.update("insert into store_setmeal values(1,10,1,1,0),(1,10,2,0,0),"
                + "(1,11,3,1,0),(2,20,4,1,0)");

        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(ds);
        factory.setMapperLocations(new ClassPathResource("mapper/ReportMapper.xml"));
        session = factory.getObject().openSession();
        // Authorization is isolated here; database grouping and response mapping remain real.
        rbac = mock(RbacService.class);
        ReportServiceImpl reports = new ReportServiceImpl(session.getMapper(ReportMapper.class), rbac);
        mvc = MockMvcBuilders.standaloneSetup(new WorkspaceController(reports))
                .addInterceptors(new RbacInterceptor(rbac)).build();
        RequestScope.set(new RequestScope(1L, null, 1L, 10L, Collections.singleton(10L)));
    }

    @AfterEach
    void cleanup() {
        RequestScope.clear();
        if (session != null) session.close();
    }

    @ParameterizedTest
    @CsvSource({"overviewDishes,2,3", "overviewSetmeals,1,1"})
    void countsMenuStatusForCurrentTenantAndStore(String endpoint, int sold, int discontinued) throws Exception {
        mvc.perform(get("/admin/workspace/" + endpoint))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.sold").value(sold))
                .andExpect(jsonPath("$.data.discontinued").value(discontinued));
    }

    @ParameterizedTest
    @ValueSource(strings = {"overviewDishes", "overviewSetmeals"})
    void emptyStoreReturnsZeroCounts(String endpoint) throws Exception {
        RequestScope.set(new RequestScope(1L, null, 1L, 12L, Collections.singleton(12L)));
        mvc.perform(get("/admin/workspace/" + endpoint))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sold").value(0))
                .andExpect(jsonPath("$.data.discontinued").value(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"overviewDishes", "overviewSetmeals"})
    void deniesRequestsWithoutWorkspacePermission(String endpoint) throws Exception {
        doThrow(new SecurityException("Denied")).when(rbac).require(PermissionAction.WORKSPACE_READ);
        mvc.perform(get("/admin/workspace/" + endpoint)).andExpect(status().isForbidden());
    }
}
