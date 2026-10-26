package com.sky.test;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.dto.EmployeeLoginDTO;
import com.sky.dto.PasswordEditDTO;
import com.sky.dto.UserLoginDTO;
import com.sky.entity.Employee;
import com.sky.properties.JwtProperties;
import com.sky.vo.EmployeeLoginVO;
import com.sky.vo.UserLoginVO;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanWrapperImpl;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SensitiveObjectLoggingTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @ParameterizedTest(name = "{0}")
    @MethodSource("sensitiveObjects")
    void objectLoggingOmitsSensitiveValues(String description, Object model, String visibleDetail,
                                          Map<String, String> sensitiveFields) {
        String message = captureFormattedLog(model);

        assertTrue(message.contains(visibleDetail), "Ordinary object details should still be logged");
        assertAll(sensitiveFields.entrySet().stream().map(field -> (Executable) () ->
                assertFalse(message.contains(field.getValue()),
                        "Formatted log contains sensitive field " + field.getKey())));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sensitiveObjects")
    void sensitiveFieldsRemainInJsonResponses(String description, Object model, String visibleDetail,
                                              Map<String, String> sensitiveFields) throws Exception {
        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(model));

        for (Map.Entry<String, String> field : sensitiveFields.entrySet()) {
            assertEquals(field.getValue(), json.path(field.getKey()).asText(),
                    "JSON serialization must retain " + field.getKey());
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sensitiveObjects")
    void sensitiveFieldsRemainReadableFromJson(String description, Object model, String visibleDetail,
                                               Map<String, String> sensitiveFields) throws Exception {
        String json = objectMapper.writeValueAsString(sensitiveFields);
        Object decoded = objectMapper.readValue(json, model.getClass());
        BeanWrapperImpl decodedProperties = new BeanWrapperImpl(decoded);

        for (Map.Entry<String, String> field : sensitiveFields.entrySet()) {
            assertEquals(field.getValue(), decodedProperties.getPropertyValue(field.getKey()),
                    "JSON deserialization must retain " + field.getKey());
        }
    }

    private String captureFormattedLog(Object model) {
        Logger logger = (Logger) LoggerFactory.getLogger(SensitiveObjectLoggingTest.class);
        Level previousLevel = logger.getLevel();
        boolean previousAdditive = logger.isAdditive();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.setContext(logger.getLoggerContext());
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
        logger.setAdditive(false);
        try {
            logger.info("Authentication model: {}", model);
            assertEquals(1, appender.list.size(), "The logger must produce an actual event");
            return appender.list.get(0).getFormattedMessage();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
            logger.setLevel(previousLevel);
            logger.setAdditive(previousAdditive);
        }
    }

    private static Stream<Arguments> sensitiveObjects() {
        EmployeeLoginDTO employeeLogin = new EmployeeLoginDTO();
        employeeLogin.setUsername("logging-test-employee");
        employeeLogin.setPassword("sensitive-test-login-password");

        PasswordEditDTO passwordEdit = new PasswordEditDTO();
        passwordEdit.setEmpId(73L);
        passwordEdit.setOldPassword("sensitive-test-old-password");
        passwordEdit.setNewPassword("sensitive-test-new-password");

        EmployeeLoginVO employeeResult = EmployeeLoginVO.builder().id(73L)
                .userName("logging-test-employee-result").token("sensitive-test-admin-token").build();
        UserLoginVO userResult = UserLoginVO.builder().id(74L).openid("logging-test-openid")
                .token("sensitive-test-user-token").build();
        Employee employee = Employee.builder().id(73L).username("logging-test-employee-entity")
                .password("sensitive-test-stored-password").build();

        UserLoginDTO userLogin = new UserLoginDTO();
        userLogin.setCode("sensitive-test-wechat-code");

        JwtProperties jwt = new JwtProperties();
        jwt.setAdminSecretKey("sensitive-test-admin-secret");
        jwt.setUserSecretKey("sensitive-test-user-secret");
        jwt.setAdminTokenName("X-Logging-Test-Admin");

        return Stream.of(
                Arguments.of("employee login request", employeeLogin, "username=logging-test-employee",
                        fields("password", "sensitive-test-login-password")),
                Arguments.of("password edit request", passwordEdit, "empId=73",
                        fields("oldPassword", "sensitive-test-old-password",
                                "newPassword", "sensitive-test-new-password")),
                Arguments.of("employee login response", employeeResult, "userName=logging-test-employee-result",
                        fields("token", "sensitive-test-admin-token")),
                Arguments.of("user login response", userResult, "id=74",
                        fields("token", "sensitive-test-user-token")),
                Arguments.of("employee entity", employee, "username=logging-test-employee-entity",
                        fields("password", "sensitive-test-stored-password")),
                Arguments.of("user login request", userLogin, "UserLoginDTO(",
                        fields("code", "sensitive-test-wechat-code")),
                Arguments.of("JWT properties", jwt, "adminTokenName=X-Logging-Test-Admin",
                        fields("adminSecretKey", "sensitive-test-admin-secret",
                                "userSecretKey", "sensitive-test-user-secret"))
        );
    }

    private static Map<String, String> fields(String... namesAndValues) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (int i = 0; i < namesAndValues.length; i += 2) {
            fields.put(namesAndValues[i], namesAndValues[i + 1]);
        }
        return fields;
    }
}
