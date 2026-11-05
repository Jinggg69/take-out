package com.junzhou.test;

import com.junzhou.dto.EmployeeDTO;
import com.junzhou.entity.Employee;
import com.junzhou.mapper.EmployeeMapper;
import com.junzhou.service.PasswordHashService;
import com.junzhou.service.impl.EmployeeServiceImpl;
import com.junzhou.service.impl.PasswordHashServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PasswordHashServiceTest {
    private final PasswordHashService hashes = new PasswordHashServiceImpl();

    @Test
    void newEmployeesWithSamePasswordReceiveDistinctVerifiableHashes() {
        EmployeeServiceImpl service = new EmployeeServiceImpl();
        EmployeeMapper mapper = mock(EmployeeMapper.class);
        ReflectionTestUtils.setField(service, "employeeMapper", mapper);
        ReflectionTestUtils.setField(service, "passwordHashes", hashes);
        service.save(new EmployeeDTO());
        service.save(new EmployeeDTO());
        ArgumentCaptor<Employee> employees = ArgumentCaptor.forClass(Employee.class);
        verify(mapper, times(2)).insert(employees.capture());
        String first = employees.getAllValues().get(0).getPassword();
        String second = employees.getAllValues().get(1).getPassword();
        assertNotEquals(first, second);
        assertTrue(first.startsWith("{bcrypt}"));
        assertTrue(hashes.matches("123456", first));
        assertTrue(hashes.matches("123456", second));
        assertFalse(hashes.matches("123456", "123456"));
        assertFalse(hashes.matches("123456", "{bcrypt}invalid"));
    }

    @Test
    void multibyteLegacyPasswordsKeepTheirSuffixBeyondBcryptLimit() {
        String prefix = String.join("", java.util.Collections.nCopies(24, "鱼"));
        String encoded = hashes.encode(prefix + "A");
        assertTrue(hashes.matches(prefix + "A", encoded));
        assertFalse(hashes.matches(prefix + "B", encoded));
        assertFalse(hashes.matches(prefix + "A", hashes.encode(prefix)));
    }
}
