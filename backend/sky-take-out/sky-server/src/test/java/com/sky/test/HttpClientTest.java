package com.sky.test;

import com.sky.constant.JwtClaimsConstant;
import com.sky.controller.admin.EmployeeController;
import com.sky.controller.user.ShopController;
import com.sky.dto.EmployeeLoginDTO;
import com.sky.entity.Employee;
import com.sky.handler.GlobalExceptionHandler;
import com.sky.json.JacksonObjectMapper;
import com.sky.properties.JwtProperties;
import com.sky.service.EmployeeService;
import com.sky.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 离线验证店铺状态和员工登录接口；外部依赖在控制器边界模拟。
 */
public class HttpClientTest {
    private static final String SECRET = "http-controller-test-secret";
    private final JacksonObjectMapper json = new JacksonObjectMapper();

    @Test
    public void testGet() throws Exception {
        RedisTemplate<String, Object> redis = mock(RedisTemplate.class);
        ValueOperations<String, Object> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("SHOP_STATUS")).thenReturn(0);
        ShopController controller = new ShopController();
        ReflectionTestUtils.setField(controller, "redisTemplate", redis);

        mvc(controller).perform(get("/user/shop/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data").value(0));
    }

    @Test
    public void testPost() throws Exception {
        EmployeeService employees = mock(EmployeeService.class);
        Employee employee = Employee.builder().id(42L).username("admin").name("测试管理员").build();
        when(employees.login(argThat((EmployeeLoginDTO login) -> login != null
                && "admin".equals(login.getUsername()) && "test-password".equals(login.getPassword()))))
                .thenReturn(employee);
        JwtProperties jwt = new JwtProperties();
        jwt.setAdminSecretKey(SECRET);
        jwt.setAdminTtl(60000L);
        EmployeeController controller = new EmployeeController();
        ReflectionTestUtils.setField(controller, "employeeService", employees);
        ReflectionTestUtils.setField(controller, "jwtProperties", jwt);

        MvcResult result = mvc(controller).perform(post("/admin/employee/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"test-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.data.id").value("42"))
                .andExpect(jsonPath("$.data.userName").value("admin"))
                .andExpect(jsonPath("$.data.name").value("测试管理员"))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andReturn();
        String token = json.readTree(result.getResponse().getContentAsByteArray())
                .path("data").path("token").asText();
        Claims claims = JwtUtil.parseJWT(SECRET, token);
        assertEquals(42, ((Number) claims.get(JwtClaimsConstant.EMP_ID)).intValue());
        assertNotNull(claims.getExpiration());
    }

    private MockMvc mvc(Object controller) {
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(json))
                .build();
    }
}
