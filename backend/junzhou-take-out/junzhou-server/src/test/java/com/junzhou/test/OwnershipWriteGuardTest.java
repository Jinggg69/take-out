package com.junzhou.test;

import com.junzhou.context.RequestScope;
import org.apache.ibatis.mapping.*;
import org.apache.ibatis.session.*;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exercise the production SQL builders: missing scope must fail before JDBC sees a write. */
class OwnershipWriteGuardTest {
    @Test void everyBusinessWriteRequiresScopeBeforeReachingTheDatabase() throws Exception {
        RequestScope.clear();
        JdbcDataSource db=new JdbcDataSource(); db.setURL("jdbc:h2:mem:"+UUID.randomUUID());
        SqlSessionFactoryBean bean=new SqlSessionFactoryBean(); bean.setDataSource(db);
        bean.setTypeAliasesPackage("com.junzhou.entity");
        bean.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/*.xml"));
        Configuration configuration=bean.getObject().getConfiguration();
        org.apache.ibatis.io.ResolverUtil<Object> resolver=new org.apache.ibatis.io.ResolverUtil<>();
        resolver.find(new org.apache.ibatis.io.ResolverUtil.IsA(Object.class),"com.junzhou.mapper");
        for(Class<?> mapper:resolver.getClasses())
            if(mapper.isInterface() && mapper.isAnnotationPresent(org.apache.ibatis.annotations.Mapper.class)
                    && !configuration.hasMapper(mapper)) configuration.addMapper(mapper);
        Map<String,Object> parameters=new HashMap<>();
        for(String name:Arrays.asList("list","flavors","setmealDishes"))
            parameters.put(name,Collections.singletonList(new HashMap<String,Object>()));
        for(String name:Arrays.asList("ids","dishIds","setmealIds")) parameters.put(name,Collections.singletonList(1L));
        int checked=0;
        Set<String> ids=new TreeSet<>(configuration.getMappedStatementNames());
        for(String id:ids) {
            if(!id.contains(".")) continue;
            // These are credential-verified global identities, not tenant-owned business records.
            if(id.startsWith("com.junzhou.mapper.UserMapper.") || id.startsWith("com.junzhou.mapper.AdminSessionMapper.")) continue;
            MappedStatement statement=configuration.getMappedStatement(id);
            if(statement.getSqlCommandType()!=SqlCommandType.INSERT && statement.getSqlCommandType()!=SqlCommandType.UPDATE
                    && statement.getSqlCommandType()!=SqlCommandType.DELETE) continue;
            RuntimeException failure=assertThrows(RuntimeException.class,()->statement.getBoundSql(parameters),id);
            Throwable cause=failure;
            while(cause!=null && !(cause instanceof SecurityException)) cause=cause.getCause();
            assertNotNull(cause,"Expected missing-scope rejection for "+id+", got "+failure);
            checked++;
        }
        assertTrue(checked>=35,"Business write inventory unexpectedly shrank: "+checked);
        assertNull(RequestScope.current());
    }
}
