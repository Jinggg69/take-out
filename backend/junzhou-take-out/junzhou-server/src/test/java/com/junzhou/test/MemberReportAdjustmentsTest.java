package com.junzhou.test;
import com.junzhou.service.MemberReportAdjustments;
import com.junzhou.context.RequestScope;
import com.junzhou.report.*;
import org.h2.jdbcx.JdbcDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class MemberReportAdjustmentsTest {
 @Test void reportsExcludeGiftReceiptsAndIncludeWalletPrincipalRefundsWithinScope(){
  JdbcDataSource ds=new JdbcDataSource();ds.setURL("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1");JdbcTemplate db=new JdbcTemplate(ds);
  db.execute("create table orders(id bigint,tenant_id bigint,store_id bigint,checkout_time timestamp)");
  db.execute("create table member_order_asset(order_id bigint,tenant_id bigint,store_id bigint,gift bigint,principal bigint,status varchar(20),refunded_at timestamp)");
  db.update("insert into orders values(1,1,10,'2026-11-17 12:00:00'),(2,2,10,'2026-11-17 12:00:00'),(3,1,11,'2026-11-17 12:00:00')");
  db.update("insert into member_order_asset values(1,1,10,2000,3000,'REFUNDED','2026-11-17 12:30:00'),(2,2,10,9000,9000,'REFUNDED','2026-11-17 12:30:00'),(3,1,11,9000,9000,'REFUNDED','2026-11-17 12:30:00')");
  RequestScope.set(new RequestScope(7L,null,1L,10L,Collections.singleton(10L)));
  try {
   ReportFilter f=new ReportFilter(1L,Collections.singletonList(10L),LocalDateTime.parse("2026-11-17T00:00:00"),LocalDateTime.parse("2026-11-18T00:00:00"));
   ReportRow day=new ReportRow();day.setDay("2026-11-17");day.setReceived(new BigDecimal("100.00"));day.setRefunded(new BigDecimal("50.00"));
   SortedMap<String,ReportRow> days=new TreeMap<>();days.put(day.getDay(),day);
   new MemberReportAdjustments(db).daily(f,days);
   assertEquals(0,new BigDecimal("80").compareTo(day.getReceived()));
   assertEquals(0,new BigDecimal("80").compareTo(day.getRefunded()));
  } finally { RequestScope.clear(); }
 }
}
