package com.sky.test;

import com.sky.aspect.AutoFillAspect;
import com.sky.context.BaseContext;
import com.sky.entity.Category;
import com.sky.entity.Employee;
import com.sky.mapper.CategoryMapper;
import com.sky.mapper.EmployeeMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

class AutoFillAspectTest {

    @AfterEach
    void clearCurrentId() {
        BaseContext.removeCurrentId();
    }

    @Test
    void insertFillsCreationAndUpdateFieldsOnAnnotatedMappers() {
        BaseContext.setCurrentId(42L);
        EmployeeMapper employeeMapper = proxy(mock(EmployeeMapper.class), EmployeeMapper.class);
        CategoryMapper categoryMapper = proxy(mock(CategoryMapper.class), CategoryMapper.class);
        Employee employee = new Employee();
        Category category = new Category();

        employeeMapper.insert(employee);
        categoryMapper.insert(category);

        assertNotNull(employee.getCreateTime());
        assertEquals(employee.getCreateTime(), employee.getUpdateTime());
        assertEquals(42L, employee.getCreateUser());
        assertEquals(42L, employee.getUpdateUser());
        assertNotNull(category.getCreateTime());
        assertEquals(category.getCreateTime(), category.getUpdateTime());
        assertEquals(42L, category.getCreateUser());
        assertEquals(42L, category.getUpdateUser());
    }

    @Test
    void updateFillsOnlyUpdateFieldsOnAnnotatedMappers() {
        BaseContext.setCurrentId(43L);
        EmployeeMapper employeeMapper = proxy(mock(EmployeeMapper.class), EmployeeMapper.class);
        CategoryMapper categoryMapper = proxy(mock(CategoryMapper.class), CategoryMapper.class);
        LocalDateTime originalTime = LocalDateTime.of(2020, 1, 1, 0, 0);
        Employee employee = Employee.builder().createTime(originalTime).createUser(7L).build();
        Category category = Category.builder().createTime(originalTime).createUser(8L).build();

        employeeMapper.update(employee);
        categoryMapper.update(category);

        assertSame(originalTime, employee.getCreateTime());
        assertEquals(7L, employee.getCreateUser());
        assertNotNull(employee.getUpdateTime());
        assertEquals(43L, employee.getUpdateUser());
        assertSame(originalTime, category.getCreateTime());
        assertEquals(8L, category.getCreateUser());
        assertNotNull(category.getUpdateTime());
        assertEquals(43L, category.getUpdateUser());
    }

    private <T> T proxy(T mapper, Class<T> type) {
        AspectJProxyFactory factory = new AspectJProxyFactory(mapper);
        factory.addAspect(new AutoFillAspect());
        return type.cast(factory.getProxy());
    }
}
