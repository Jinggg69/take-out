package com.junzhou.test;
import com.junzhou.context.RequestScope;
import com.junzhou.mapper.ReportMapper;
import com.junzhou.report.ReportFilter;
import com.junzhou.service.RbacService;
import com.junzhou.service.impl.ReportServiceImpl;
import com.junzhou.security.PermissionAction;
import org.junit.jupiter.api.*;
import org.h2.jdbcx.JdbcDataSource;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ReportStage6Test {
 @AfterEach void clear(){RequestScope.clear();}
 @Test void validatesRangeAndEveryStoreAction(){
  RbacService rbac=mock(RbacService.class); ReportServiceImpl service=new ReportServiceImpl(mock(ReportMapper.class),rbac);
  RequestScope.set(new RequestScope(1L,null,1L,1L,new HashSet<>(Arrays.asList(1L,2L))));
  when(rbac.permits(1L,1L,1L,PermissionAction.REPORT_READ)).thenReturn(true);
  assertThrows(SecurityException.class,()->service.filter("2026-11-01","2026-11-02",Arrays.asList(1L,2L),false));
  assertThrows(SecurityException.class,()->service.filter("2026-11-01","2026-11-02",null,true));
  assertThrows(RuntimeException.class,()->service.filter("2026-11-03","2026-11-02",null,false));
  assertThrows(RuntimeException.class,()->service.filter("2020-01-01","2026-11-02",null,false));
  assertEquals(LocalDateTime.of(2026,11,3,0,0),service.filter("2026-11-01","2026-11-02",null,false).getUntil());
  RequestScope.clear();assertThrows(SecurityException.class,()->service.filter(null,null,null,false));
 }
 @Test void directExportRequiresBothActionsAndRevocationIsRechecked() throws Exception {
  ReportMapper mapper=mock(ReportMapper.class);RbacService rbac=mock(RbacService.class);
  ReportServiceImpl service=new ReportServiceImpl(mapper,rbac);
  RequestScope.set(new RequestScope(1L,null,1L,1L,Collections.singleton(1L)));
  when(rbac.permits(anyLong(),anyLong(),anyLong(),any())).thenReturn(true);
  ReportFilter f=service.filter("2026-11-01","2026-11-02",null,true);
  when(rbac.permits(1L,1L,1L,PermissionAction.REPORT_EXPORT)).thenReturn(false);
  assertThrows(SecurityException.class,()->service.export(f));verifyNoInteractions(mapper);
  doThrow(new SecurityException()).when(rbac).require(PermissionAction.REPORT_EXPORT);
  org.springframework.test.web.servlet.MockMvc mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new com.junzhou.controller.admin.ReportController(service)).addInterceptors(new com.junzhou.interceptor.RbacInterceptor(rbac)).build();
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/admin/report/export").param("begin","2026-11-01").param("end","2026-11-02")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
  verifyNoInteractions(mapper);
 }
 @Test void eventsUseTheirOwnDayAndScopeIncludingLatePayment() throws Exception {
  JdbcDataSource ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");JdbcTemplate db=new JdbcTemplate(ds);
  db.execute("create table orders(id bigint,tenant_id bigint,store_id bigint,number varchar,order_time timestamp,checkout_time timestamp,delivery_time timestamp,cancel_time timestamp,amount decimal(10,2),status int,user_id bigint,fulfillment_type varchar)");
  db.execute("create table refund_request(order_id bigint,status varchar,updated_at timestamp,amount_fen bigint)");
  db.execute("create table order_detail(id bigint,order_id bigint,dish_id bigint,setmeal_id bigint,name varchar,number int,amount decimal(10,2))");
  db.update("insert into orders values(1,1,1,'a','2026-11-01 23:00','2026-11-02 00:00','2026-11-02 10:00',null,10,5,1,'DELIVERY'),(2,1,1,'b','2026-11-01 21:00','2026-11-02 12:00',null,'2026-11-01 22:00',20,6,2,'PICKUP'),(3,1,2,'c','2026-11-02 10:00','2026-11-02 11:00',null,null,30,3,3,'DINE_IN'),(4,2,3,'d','2026-11-02 10:00','2026-11-02 11:00',null,null,999,3,4,'DELIVERY')");
  db.update("insert into refund_request values(1,'SUCCESS','2026-11-03 00:00',1000),(2,'PROCESSING','2026-11-02 13:00',2000)");
  db.update("insert into order_detail values(1,1,1,null,'fish',2,5),(2,3,null,1,'meal',1,30),(3,2,2,null,'late',1,20)");
  SqlSessionFactoryBean b=new SqlSessionFactoryBean();b.setDataSource(ds);b.setMapperLocations(new ClassPathResource("mapper/ReportMapper.xml"));
  try(org.apache.ibatis.session.SqlSession session=b.getObject().openSession()){
   ReportMapper mapper=session.getMapper(ReportMapper.class);RbacService rbac=mock(RbacService.class);
   when(rbac.permits(anyLong(),anyLong(),anyLong(),any())).thenReturn(true);
   RequestScope.set(new RequestScope(1L,null,1L,1L,new HashSet<>(Arrays.asList(1L,2L))));
   ReportServiceImpl service=new ReportServiceImpl(mapper,rbac);
   ReportFilter f=service.filter("2026-11-01","2026-11-03",null,false);
   Map<String,Object> t=service.turnover(f);assertEquals("0.00,30.00,0.00",t.get("turnoverList"));assertEquals("0.00,0.00,10.00",t.get("refundList"));assertEquals("0.00,30.00,-10.00",t.get("netList"));
   Map<String,Object> o=service.orders(f);assertEquals("2,0,0",o.get("orderCountList"));assertEquals("0,2,0",o.get("paidOrderCountList"));assertEquals("0,1,0",o.get("validOrderCountList"));
   assertEquals("late",service.top10(f).get("nameList"));
   ReportFilter before=service.filter("2026-11-02","2026-11-02",Arrays.asList(1L,2L),false);
   assertEquals("60.00",service.turnover(before).get("turnoverList"));assertEquals("fish,meal,late",service.top10(before).get("nameList"));
   try(org.apache.poi.xssf.usermodel.XSSFWorkbook wb=new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(service.export(before)))){assertEquals(4,wb.getNumberOfSheets());assertEquals(1,wb.getSheetAt(0).getLastRowNum());assertTrue(wb.getSheetAt(2).getLastRowNum()>0);}
  }
 }
}
