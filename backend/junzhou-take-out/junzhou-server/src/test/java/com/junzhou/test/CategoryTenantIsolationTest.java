package com.junzhou.test;

import com.junzhou.dto.CategoryPageQueryDTO;
import com.junzhou.entity.Category;
import com.junzhou.context.RequestScope;
import com.junzhou.dto.CategoryDTO;
import com.junzhou.mapper.CategoryMapper;
import com.junzhou.service.impl.CategoryServiceImpl;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.UUID;
import java.util.Collections;
import org.springframework.test.util.ReflectionTestUtils;
import com.junzhou.handler.GlobalExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CategoryTenantIsolationTest {
    @RestController static class DeniedController {
        @GetMapping("/denied") public String denied() { throw new SecurityException("private details"); }
    }

    @Test void unauthorizedRequestsReturnForbidden() throws Exception {
        MockMvcBuilders.standaloneSetup(new DeniedController()).setControllerAdvice(new GlobalExceptionHandler())
                .build().perform(get("/denied")).andExpect(status().isForbidden());
    }
    @Test void serviceRejectsMissingScopeAndForeignCategory() {
        CategoryMapper mapper = mock(CategoryMapper.class);
        when(mapper.lockOwned(anyLong(), anyLong())).thenReturn(null);
        CategoryServiceImpl service = new CategoryServiceImpl();
        ReflectionTestUtils.setField(service, "categoryMapper", mapper);
        try {
            RequestScope.clear();
            assertThrows(SecurityException.class, () -> service.list(1));
            RequestScope.set(new RequestScope(42L, null, 1L, 1L, Collections.singleton(1L)));
            CategoryDTO dto = new CategoryDTO();
            dto.setId(2L);
            dto.setName("changed");
            assertThrows(SecurityException.class, () -> service.update(dto));
            assertThrows(SecurityException.class, () -> service.deleteById(2L));
            verify(mapper).update(any(Category.class), eq(1L));
            verify(mapper, never()).deleteById(anyLong(), anyLong());
        } finally {
            RequestScope.clear();
        }
    }

    @Test void queriesAndMutationsCannotCrossTenantAndInsertSetsOwnership() throws Exception {
        JdbcDataSource db = new JdbcDataSource();
        db.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(db);
        jdbc.execute("create table category (id bigint auto_increment primary key,tenant_id bigint not null, type int,name varchar(32),sort int,status int,create_time timestamp,update_time timestamp,create_user bigint,update_user bigint)");
        jdbc.update("insert into category(id,tenant_id,type,name,sort,status) values(1,1,1,'A',1,1),(2,2,1,'B',1,1)");
        SqlSessionFactoryBean bean = new SqlSessionFactoryBean(); bean.setDataSource(db);
        bean.setMapperLocations(new ClassPathResource("mapper/CategoryMapper.xml"));
        SqlSessionFactory factory = bean.getObject();
        RequestScope.set(new RequestScope(42L, null, 1L, 1L, Collections.singleton(1L)));
        try (SqlSession session = factory.openSession(true)) {
            CategoryMapper categories = session.getMapper(CategoryMapper.class);
            assertEquals(1, categories.list(1, 1L).size());
            assertEquals("A", categories.list(1, 1L).get(0).getName());
            assertEquals(1, categories.pageQuery(new CategoryPageQueryDTO(), 1L).size());
            Category update = Category.builder().id(2L).name("altered").build();
            assertEquals(0, categories.update(update, 1L));
            assertEquals(0, categories.deleteById(2L, 1L));
            assertEquals("B", jdbc.queryForObject("select name from category where id=2", String.class));
            Category added = Category.builder().type(1).name("new").sort(2).status(1).build();
            assertEquals(0, categories.insert(added, 2L));
            RequestScope.set(new RequestScope(43L, null, 2L, 2L, Collections.singleton(2L)));
            categories.insert(added, 2L);
            assertEquals(2L, jdbc.queryForObject("select tenant_id from category where name='new'", Long.class));
            RequestScope.clear();
            assertThrows(RuntimeException.class, () -> categories.list(1, 1L));
        } finally { RequestScope.clear(); }
    }
}
