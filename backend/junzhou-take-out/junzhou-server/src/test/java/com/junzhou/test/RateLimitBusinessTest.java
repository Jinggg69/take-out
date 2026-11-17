package com.junzhou.test;
import com.junzhou.ratelimit.*;
import com.junzhou.controller.admin.ReportController;
import com.junzhou.service.ReportService;
import com.junzhou.handler.GlobalExceptionHandler;
import com.junzhou.report.ReportFilter;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class RateLimitBusinessTest {
 @Configuration @EnableConfigurationProperties(RateLimitProperties.class) static class Binding {}
 @Test void disabledFixtureIsExcludedByBootComponentScan() throws Exception {
  org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider scanner =
   new org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider(true);
  java.lang.reflect.Constructor<?> constructor = Class.forName("org.springframework.boot.test.context.filter.TestTypeExcludeFilter").getDeclaredConstructor();
  constructor.setAccessible(true);
  scanner.addExcludeFilter((org.springframework.core.type.filter.TypeFilter) constructor.newInstance());
  assertFalse(scanner.findCandidateComponents("com.junzhou.test.support").stream().anyMatch(
   definition -> definition.getBeanClassName().equals("com.junzhou.test.support.DisabledRateLimitConfiguration")));
 }
 @Test void yamlDefaultsMatchPropertyDefaults() throws Exception {
  org.springframework.core.env.PropertySource<?> yaml = new org.springframework.boot.env.YamlPropertySourceLoader()
   .load("application", new org.springframework.core.io.ClassPathResource("application.yml")).get(0);
  new ApplicationContextRunner().withUserConfiguration(Binding.class)
   .withInitializer(context -> context.getEnvironment().getPropertySources().addLast(yaml))
   .run(context -> {
    assertNull(context.getStartupFailure());
    RateLimitProperties actual=context.getBean(RateLimitProperties.class), expected=new RateLimitProperties();
    assertEquals(expected.isEnabled(),actual.isEnabled()); assertEquals(expected.getNamespace(),actual.getNamespace());
    RateLimitProperties.Rule[] a={actual.getLoginIp(),actual.getLoginFailure(),actual.getReminder(),actual.getPaymentConfirm(),actual.getReportExport()};
    RateLimitProperties.Rule[] e={expected.getLoginIp(),expected.getLoginFailure(),expected.getReminder(),expected.getPaymentConfirm(),expected.getReportExport()};
    for(int n=0;n<a.length;n++){assertEquals(e[n].getLimit(),a[n].getLimit());assertEquals(e[n].getWindowMillis(),a[n].getWindowMillis());}
   });
 }
 @Test void springBindsDisabledAndAuthenticationStillExecutesWithoutRedis() {
  RedisRateLimiter redis = mock(RedisRateLimiter.class);
  new ApplicationContextRunner().withUserConfiguration(Binding.class)
   .withBean(RedisRateLimiter.class, () -> redis).withBean(RateLimitGuard.class).withBean(ClientIpResolver.class).withBean(LoginAttemptGuard.class)
   .withPropertyValues("sky.rate-limit.enabled=false", "sky.rate-limit.namespace=integration", "sky.rate-limit.login-ip.limit=7")
   .run(context -> {
    assertNull(context.getStartupFailure());
    assertEquals(7, context.getBean(RateLimitProperties.class).getLoginIp().getLimit());
    com.junzhou.dto.EmployeeLoginDTO input = new com.junzhou.dto.EmployeeLoginDTO(); input.setUsername("employee");
    java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
    context.getBean(LoginAttemptGuard.class).login(input, new org.springframework.mock.web.MockHttpServletRequest(), () -> { calls.incrementAndGet(); return null; });
    assertEquals(1, calls.get()); verifyNoInteractions(redis);
   });
 }
 @Test void exportChecksAuthorizationBeforeQuotaAndNeverGeneratesRejectedFiles() throws Exception {
  ReportService reports = mock(ReportService.class); RateLimitGuard guard = mock(RateLimitGuard.class);
  ReportController controller = new ReportController(reports, guard);
  when(reports.filter(any(),any(),any(),eq(true))).thenThrow(new SecurityException());
  assertThrows(SecurityException.class, () -> controller.export("a","b",null)); verifyNoInteractions(guard);
  reset(reports);
  ReportFilter filter = new ReportFilter(1L, java.util.Collections.singletonList(1L), java.time.LocalDateTime.now(), java.time.LocalDateTime.now());
  when(reports.filter(any(),any(),any(),eq(true))).thenReturn(filter);
  doThrow(new RateLimitExceededException("report-export",1001)).when(guard).reportExport();
  org.springframework.test.web.servlet.MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
  mvc.perform(get("/admin/report/export").param("begin","a").param("end","b"))
   .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After","2")).andExpect(header().string("Cache-Control","no-store"));
  verify(reports,never()).export(any());
  doThrow(new RateLimitUnavailableException()).when(guard).reportExport();
  mvc.perform(get("/admin/report/export").param("begin","a").param("end","b"))
   .andExpect(status().isServiceUnavailable()).andExpect(header().string("Retry-After","3")).andExpect(header().string("Cache-Control","no-store"))
   .andExpect(jsonPath("$.msg").value(RateLimitUnavailableException.PUBLIC_MESSAGE));
  verify(reports,never()).export(any());
 }
 @Test void orderOwnershipPrecedesQuotaAndRejectedRequestsHaveNoSideEffects() {
  com.junzhou.service.impl.OrderServiceImpl service = new com.junzhou.service.impl.OrderServiceImpl();
  com.junzhou.mapper.OrderMapper orders = mock(com.junzhou.mapper.OrderMapper.class);
  RateLimitGuard guard = mock(RateLimitGuard.class);
  com.junzhou.websocket.WebSocketServer sockets = mock(com.junzhou.websocket.WebSocketServer.class);
  org.springframework.test.util.ReflectionTestUtils.setField(service,"orderMapper",orders);
  org.springframework.test.util.ReflectionTestUtils.setField(service,"rateLimitGuard",guard);
  org.springframework.test.util.ReflectionTestUtils.setField(service,"webSocketServer",sockets);
  com.junzhou.entity.Orders order = new com.junzhou.entity.Orders(); order.setId(1L); order.setUserId(8L);
  when(orders.getById(1L)).thenReturn(order);
  com.junzhou.context.RequestScope.set(new com.junzhou.context.RequestScope(null,7L,1L,1L,java.util.Collections.emptySet()));
  try {
   assertThrows(com.junzhou.exception.OrderBusinessException.class,()->service.reminder(1L));
   assertThrows(com.junzhou.exception.OrderBusinessException.class,()->service.confirmPayment(1L)); verifyNoInteractions(guard,sockets);
   order.setUserId(7L);
   doThrow(new RateLimitExceededException("order-reminder",1000)).when(guard).reminder(1L);
   doThrow(new RateLimitExceededException("payment-confirm",1000)).when(guard).paymentConfirm(1L);
   assertThrows(RateLimitExceededException.class,()->service.reminder(1L));
   assertThrows(RateLimitExceededException.class,()->service.confirmPayment(1L)); verifyNoInteractions(sockets);
  } finally { com.junzhou.context.RequestScope.clear(); }
 }
}
