package com.junzhou.test;

import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.mock.web.MockServletContext;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

class PlatformAuthenticationTest {
 private AnnotationConfigWebApplicationContext ctx;
 private MockMvc mvc;
 private JdbcTemplate db;
 @BeforeEach void setup() {
  ctx=new AnnotationConfigWebApplicationContext(); ctx.setServletContext(new MockServletContext());
  ctx.register(AdminAuthenticationTest.DatabaseConfiguration.class, com.junzhou.test.support.LegacyMvcScopeConfiguration.class,
    com.junzhou.config.WebMvcConfiguration.class,com.junzhou.controller.admin.EmployeeController.class,
    com.junzhou.interceptor.JwtTokenAdminInterceptor.class, com.junzhou.interceptor.JwtTokenUserInterceptor.class,
    com.junzhou.handler.GlobalExceptionHandler.class);
  ctx.register(com.junzhou.platform.PlatformAuthService.class,com.junzhou.platform.PlatformAuthInterceptor.class,com.junzhou.platform.PlatformAuthController.class); ctx.refresh(); db=new JdbcTemplate(ctx.getBean(DataSource.class));
  db.execute("create table platform_admin(id bigint primary key, username varchar(64) unique,password varchar(255),name varchar(64),status int)");
  db.execute("create table platform_session(id varchar(64) primary key,admin_id bigint,expires_at timestamp,created_at timestamp default current_timestamp)");
  db.execute("create table tenant(id bigint primary key,status int);insert into tenant values(1,1)");
  db.execute("create table employee(id bigint primary key,username varchar(64),name varchar(64),password varchar(255),tenant_id bigint,status int,phone varchar(32),sex varchar(2),id_number varchar(32),create_time timestamp,update_time timestamp,create_user bigint,update_user bigint)");
  db.execute("create table admin_session(session_id varchar(36) primary key,employee_id bigint,expires_at bigint)");
  db.update("insert into platform_admin values(1,'platform_admin',?,'Platform',1)",ctx.getBean(com.junzhou.service.PasswordHashService.class).encode("Old123456!"));
  mvc=MockMvcBuilders.webAppContextSetup(ctx).build();
 }
 @AfterEach void cleanup(){ctx.close();}
 @Test void absentAndMerchantTokensCannotAccessPlatform() throws Exception {
  mvc.perform(get("/platform/auth/me")).andExpect(status().isUnauthorized());
  mvc.perform(get("/platform/auth/me").header("token",com.junzhou.utils.JwtUtil.createJWT("admin-auth-regression-secret",60000L,new java.util.HashMap<>(java.util.Collections.singletonMap("empId",1L))))).andExpect(status().isUnauthorized());
 }
 @Test void wrongPlatformPasswordNeverFallsBackToEmployee() throws Exception {
  db.update("insert into employee(id,username,password,tenant_id,status) values(2,'platform_admin',?,1,1)",ctx.getBean(com.junzhou.service.PasswordHashService.class).encode("Employee123!"));
  mvc.perform(post("/admin/employee/login").contentType("application/json").content("{\"username\":\"platform_admin\",\"password\":\"Employee123!\"}"))
    .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
  assertEquals(0,db.queryForObject("select count(*) from platform_session",Integer.class));
 }
 @Test void platformTokenIsIsolatedAndLogoutRevokesOnlyCurrentSession() throws Exception {
  String token=login(), second=login();
  mvc.perform(get("/platform/auth/me").header("token",token)).andExpect(status().isOk()).andExpect(jsonPath("$.data.accountType").value("PLATFORM"));
  mvc.perform(get("/admin/employee/42").header("token",token)).andExpect(status().isUnauthorized());
  mvc.perform(post("/platform/auth/logout").header("token",token)).andExpect(status().isOk());
  rejected(token); mvc.perform(get("/platform/auth/me").header("token",second)).andExpect(status().isOk());
 }
 @Test void disabledAndExpiredAndUnavailableStorageFailClosed() throws Exception {
  String token=login(); db.update("update platform_admin set status=0"); rejected(token);
  db.update("update platform_admin set status=1"); db.update("update platform_session set expires_at=timestamp '2000-01-01 00:00:00'"); rejected(token);
  token=login(); db.execute("drop table platform_session");
  mvc.perform(get("/platform/auth/me").header("token",token)).andExpect(status().isServiceUnavailable());
 }
 @Test void passwordChangeRevokesAllSessionsAndContextClears() throws Exception {
  String first=login(),second=login();
  mvc.perform(put("/platform/auth/password").header("token",first).contentType("application/json").content("{\"oldPassword\":\"Old123456!\",\"newPassword\":\"New123456!\"}"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));
  rejected(first);rejected(second);
  Class<?> context=Class.forName("com.junzhou.platform.PlatformContext");
  assertThrows(java.lang.reflect.InvocationTargetException.class,()->context.getMethod("requireAdminId").invoke(null));
 }
 private String login() throws Exception {
  byte[] body=mvc.perform(post("/admin/employee/login").contentType("application/json").content("{\"username\":\"platform_admin\",\"password\":\"Old123456!\"}"))
   .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1)).andExpect(jsonPath("$.data.accountType").value("PLATFORM")).andReturn().getResponse().getContentAsByteArray();
  return new ObjectMapper().readTree(body).path("data").path("token").asText();
 }
 private void rejected(String token) throws Exception {mvc.perform(get("/platform/auth/me").header("token",token)).andExpect(status().isUnauthorized());}
}
