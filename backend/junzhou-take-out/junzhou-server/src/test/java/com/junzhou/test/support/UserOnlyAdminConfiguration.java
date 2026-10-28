package com.junzhou.test.support;

import com.junzhou.mapper.AdminSessionMapper;
import com.junzhou.mapper.EmployeeMapper;
import com.junzhou.service.AdminSessionService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import static org.mockito.Mockito.mock;

/** Unused admin dependencies for tests that exercise only public/user routes. */
@TestConfiguration
public class UserOnlyAdminConfiguration {
    @Bean EmployeeMapper employeeMapper() { return mock(EmployeeMapper.class); }
    @Bean AdminSessionMapper adminSessionMapper() { return mock(AdminSessionMapper.class); }
    @Bean AdminSessionService adminSessionService() { return new AdminSessionService(); }
}
