package com.junzhou.service.impl;
import com.junzhou.context.RequestScope;
import com.junzhou.exception.OrderBusinessException;
import com.junzhou.mapper.ReportMapper;
import com.junzhou.report.*;
import com.junzhou.report.cache.ReportCacheKey;
import com.junzhou.report.cache.ReportResultCache;
import com.junzhou.security.PermissionAction;
import com.junzhou.service.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import java.io.*;
import java.math.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
/** Online reporting; each public operation rechecks authorization, exports use a consistent read transaction. */
@Service public class ReportServiceImpl implements ReportService {
 @org.springframework.beans.factory.annotation.Autowired(required=false) private MemberReportAdjustments memberReports;
 private static final String KIND_DISH="dish", KIND_SETMEAL="setmeal";
 private final ReportMapper mapper; private final RbacService rbac;
 private final ReportResultCache cache; private final Clock clock;
 public ReportServiceImpl(ReportMapper mapper,RbacService rbac,
                          ReportResultCache cache,@Qualifier("reportClock") Clock clock){
  this.mapper=mapper;this.rbac=rbac;this.cache=cache;this.clock=clock;
 }
 /** {@inheritDoc} */
 @Override public ReportFilter filter(String begin,String end,List<Long> stores,boolean export){
  Long tenant=RequestScope.requireEmployeeTenantId();
  LocalDate today=LocalDate.now(clock),a,b;
  try{a=begin==null?today.minusDays(30):LocalDate.parse(begin);b=end==null?today.minusDays(1):LocalDate.parse(end);}catch(RuntimeException e){throw new OrderBusinessException("日期格式应为 yyyy-MM-dd");}
  if(b.isBefore(a)||ChronoUnit.DAYS.between(a,b)>92)throw new OrderBusinessException("请选择1至93天日期范围");
  List<Long> ids=stores==null?Collections.singletonList(RequestScope.requireStoreId()):new ArrayList<>(new TreeSet<>(stores));
  if(ids.isEmpty()||ids.size()>100)throw new OrderBusinessException("请选择1至100家门店");
  ReportFilter f=new ReportFilter(tenant,Collections.unmodifiableList(ids),a.atStartOfDay(),b.plusDays(1).atStartOfDay());authorize(f,export);return f;
 }
 private void authorize(ReportFilter f,boolean export){
  Long tenant=RequestScope.requireEmployeeTenantId(); Long employee=RequestScope.current().getEmployeeId();
  if(!tenant.equals(f.getTenant())||f.getStores().isEmpty())throw new SecurityException("Invalid report scope");
  for(Long id:f.getStores())if(id==null||!rbac.permits(tenant,employee,id,PermissionAction.REPORT_READ)||(export&&!rbac.permits(tenant,employee,id,PermissionAction.REPORT_EXPORT)))throw new SecurityException("Report store denied");
 }
 /**
  * Authorization already happened; the key, the captured day and the loader all share the same filter.
  * The loader is a private computation, never a public cached method, so export cannot reach Redis.
  */
 private Map<String,Object> cached(ReportCacheKey.View view,ReportFilter f,LocalDate capturedDay,Supplier<Map<String,Object>> loader){
  return cache.getOrLoad(cache.key(view,f),view,cache.ttlSeconds(view,f,capturedDay),loader);
 }
 private SortedMap<String,ReportRow> days(ReportFilter f){
  SortedMap<String,ReportRow> days=new TreeMap<>();
  for(LocalDate d=f.getFrom().toLocalDate();d.isBefore(f.getUntil().toLocalDate());d=d.plusDays(1)){ReportRow r=new ReportRow();r.setDay(d.toString());r.setReceived(BigDecimal.ZERO);r.setRefunded(BigDecimal.ZERO);days.put(r.getDay(),r);}
  for(ReportRow r:mapper.daily(f)){ReportRow t=days.get(r.getDay());t.setReceived(t.getReceived().add(zero(r.getReceived())));t.setRefunded(t.getRefunded().add(zero(r.getRefunded())));t.setPlaced(t.getPlaced()+r.getPlaced());t.setPaid(t.getPaid()+r.getPaid());t.setCompleted(t.getCompleted()+r.getCompleted());t.setCancelled(t.getCancelled()+r.getCancelled());}if(memberReports!=null)memberReports.daily(f,days);return days;
 }
 private List<ReportRow> sales(ReportFilter f){List<ReportRow> rows=mapper.sales(f);return memberReports==null?rows:memberReports.sales(f,rows);}
 private BigDecimal zero(BigDecimal v){return v==null?BigDecimal.ZERO:v;}
 private String csv(Collection<ReportRow> rows,Function<ReportRow,Object> get){return rows.stream().map(r->{Object v=get.apply(r);return v instanceof BigDecimal?((BigDecimal)v).setScale(2,RoundingMode.HALF_UP).toPlainString():String.valueOf(v);}).collect(Collectors.joining(","));}
 private Map<String,Object> map(Object... pairs){Map<String,Object> m=new LinkedHashMap<>();for(int i=0;i<pairs.length;i+=2)m.put((String)pairs[i],pairs[i+1]);return m;}
 /** {@inheritDoc} */
 @Override public Map<String,Object> turnover(ReportFilter f){authorize(f,false);return cached(ReportCacheKey.View.REPORT_TURNOVER,f,LocalDate.now(clock),()->loadTurnover(f));}
 private Map<String,Object> loadTurnover(ReportFilter f){Collection<ReportRow> d=days(f).values();return map("dateList",csv(d,ReportRow::getDay),"turnoverList",csv(d,ReportRow::getReceived),"refundList",csv(d,ReportRow::getRefunded),"netList",csv(d,r->r.getReceived().subtract(r.getRefunded())));}
 /** {@inheritDoc} */
 @Override public Map<String,Object> orders(ReportFilter f){authorize(f,false);return cached(ReportCacheKey.View.REPORT_ORDERS,f,LocalDate.now(clock),()->loadOrders(f));}
 private Map<String,Object> loadOrders(ReportFilter f){Collection<ReportRow>d=days(f).values();long placed=d.stream().mapToLong(ReportRow::getPlaced).sum(),completed=d.stream().mapToLong(ReportRow::getCompleted).sum();return map("dateList",csv(d,ReportRow::getDay),"orderCountList",csv(d,ReportRow::getPlaced),"paidOrderCountList",csv(d,ReportRow::getPaid),"validOrderCountList",csv(d,ReportRow::getCompleted),"cancelledOrderCountList",csv(d,ReportRow::getCancelled),"totalOrderCount",placed,"validOrderCount",completed,"orderCompletionRate",cohortRate(f),"paidOrderCount",d.stream().mapToLong(ReportRow::getPaid).sum(),"cancelledOrderCount",d.stream().mapToLong(ReportRow::getCancelled).sum());}
 private double cohortRate(ReportFilter f){long total=0,done=0;for(ReportRow r:mapper.overview(f)){total+=r.getQuantity();if("5".equals(r.getName()))done=r.getQuantity();}return total==0?0:(double)done/total;}
 /** {@inheritDoc} */
 @Override public Map<String,Object> users(ReportFilter f){authorize(f,false);return cached(ReportCacheKey.View.REPORT_USERS,f,LocalDate.now(clock),()->loadUsers(f));}
 private Map<String,Object> loadUsers(ReportFilter f){SortedMap<String,ReportRow>d=days(f);long total=0;Map<String,Long> cohorts=new TreeMap<>();for(ReportRow r:mapper.users(f))cohorts.put(r.getDay(),r.getNewUsers());for(Map.Entry<String,Long>e:cohorts.entrySet())if(e.getKey().compareTo(d.firstKey())<0)total+=e.getValue();for(ReportRow r:d.values()){r.setNewUsers(cohorts.getOrDefault(r.getDay(),0L));total+=r.getNewUsers();r.setTotalUsers(total);}return map("dateList",csv(d.values(),ReportRow::getDay),"newUserList",csv(d.values(),ReportRow::getNewUsers),"totalUserList",csv(d.values(),ReportRow::getTotalUsers));}
 /** {@inheritDoc} */
 @Override public Map<String,Object> top10(ReportFilter f){authorize(f,false);return cached(ReportCacheKey.View.REPORT_TOP10,f,LocalDate.now(clock),()->loadTop10(f));}
 private Map<String,Object> loadTop10(ReportFilter f){List<ReportRow> rows=sales(f);rows=new ArrayList<>(rows.subList(0,Math.min(10,rows.size())));return map("nameList",csv(rows,r->r.getName().replace(',', '，')),"numberList",csv(rows,ReportRow::getQuantity),"items",rows);}
 private ReportFilter today(){LocalDate d=LocalDate.now(clock);return new ReportFilter(RequestScope.requireEmployeeTenantId(),Collections.singletonList(RequestScope.requireStoreId()),d.atStartOfDay(),d.plusDays(1).atStartOfDay());}
 /** {@inheritDoc} */
 @Override public Map<String,Object> business(){rbac.require(PermissionAction.WORKSPACE_READ);ReportFilter f=today();return cached(ReportCacheKey.View.WORKSPACE_BUSINESS,f,f.getFrom().toLocalDate(),()->loadBusiness(f));}
 private Map<String,Object> loadBusiness(ReportFilter f){ReportRow r=days(f).values().iterator().next();long customers=mapper.users(f).stream().filter(u->u.getDay().equals(r.getDay())).mapToLong(ReportRow::getNewUsers).sum();return map("turnover",r.getReceived(),"refundAmount",r.getRefunded(),"netAmount",r.getReceived().subtract(r.getRefunded()),"validOrderCount",r.getCompleted(),"paidOrderCount",r.getPaid(),"placedOrderCount",r.getPlaced(),"cancelledOrderCount",r.getCancelled(),"orderCompletionRate",cohortRate(f),"unitPrice",r.getPaid()==0?BigDecimal.ZERO:r.getReceived().divide(BigDecimal.valueOf(r.getPaid()),2,RoundingMode.HALF_UP),"newUsers",customers);}
 /** {@inheritDoc} */
 @Override public Map<String,Object> overview(){rbac.require(PermissionAction.WORKSPACE_READ);ReportFilter f=today();return cached(ReportCacheKey.View.WORKSPACE_ORDERS,f,f.getFrom().toLocalDate(),()->loadOverview(f));}
 private Map<String,Object> loadOverview(ReportFilter f){Map<String,Object>m=map("waitingOrders",0L,"deliveredOrders",0L,"completedOrders",0L,"cancelledOrders",0L,"allOrders",0L);long all=0;for(ReportRow r:mapper.overview(f)){all+=r.getQuantity();String key="2".equals(r.getName())?"waitingOrders":"3".equals(r.getName())?"deliveredOrders":"5".equals(r.getName())?"completedOrders":"6".equals(r.getName())?"cancelledOrders":null;if(key!=null)m.put(key,r.getQuantity());}m.put("allOrders",all);return m;}
 /** {@inheritDoc} */
 @Override public Map<String,Object> menu(String kind){
  rbac.require(PermissionAction.WORKSPACE_READ);
  ReportCacheKey.View view=KIND_DISH.equals(kind)?ReportCacheKey.View.WORKSPACE_DISHES:KIND_SETMEAL.equals(kind)?ReportCacheKey.View.WORKSPACE_SETMEALS:null;
  if(view==null)throw new IllegalArgumentException("menu kind must be dish or setmeal");
  ReportFilter f=today();return cached(view,f,f.getFrom().toLocalDate(),()->loadMenu(f,kind));
 }
 private Map<String,Object> loadMenu(ReportFilter f,String kind){long sold=0,off=0;for(ReportRow r:mapper.menu(f,kind)){if("1".equals(r.getName()))sold+=r.getQuantity();else off+=r.getQuantity();}return map("sold",sold,"discontinued",off);}
 /** {@inheritDoc} */
 @Override @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
 public byte[] export(ReportFilter f){authorize(f,true);List<ReportRow>ledger=mapper.ledger(f),items=mapper.itemDetails(f);if(memberReports!=null)memberReports.export(f,ledger,items);if(ledger.size()>50000||items.size()>50000)throw new OrderBusinessException("明细超过50000条，请缩小日期或门店范围");
  try(XSSFWorkbook wb=new XSSFWorkbook();ByteArrayOutputStream out=new ByteArrayOutputStream()){
   Sheet daily=wb.createSheet("Daily");row(daily,"Asia/Shanghai date","Received","Refund confirmed","Net","Placed","Paid","Completed","Cancelled");for(ReportRow r:days(f).values())row(daily,r.getDay(),r.getReceived(),r.getRefunded(),r.getReceived().subtract(r.getRefunded()),r.getPlaced(),r.getPaid(),r.getCompleted(),r.getCancelled());
   Sheet sales=wb.createSheet("Items");row(sales,"Dish ID","Setmeal ID","Snapshot name","Net quantity as of range end");for(ReportRow r:sales(f))row(sales,r.getDishId(),r.getSetmealId(),r.getName(),r.getQuantity());
   Sheet details=wb.createSheet("Events");row(details,"Order ID","Order number","Store ID","Fulfillment","Event time Asia/Shanghai","Event","Amount");for(ReportRow r:ledger)row(details,r.getId(),r.getNumber(),r.getStoreId(),r.getFulfillmentType(),r.getDay(),r.getEvent(),r.getAmount());
   Sheet itemDetails=wb.createSheet("Order items");row(itemDetails,"Order ID","Order number","Store ID","Fulfillment","Payment time Asia/Shanghai","Dish ID","Setmeal ID","Snapshot name","Quantity","Unit price","Inclusion at range end");for(ReportRow r:items)row(itemDetails,r.getId(),r.getNumber(),r.getStoreId(),r.getFulfillmentType(),r.getDay(),r.getDishId(),r.getSetmealId(),r.getName(),r.getQuantity(),r.getAmount(),r.getEvent());
   wb.getProperties().getCoreProperties().setDescription("Asia/Shanghai; ["+f.getFrom()+", "+f.getUntil()+"); tenant="+f.getTenant()+"; stores="+f.getStores()+"; refund=first local SUCCESS confirmation; sales=paid minus confirmed full refunds by end");
   for(Sheet s:wb){s.createFreezePane(0,1);for(int i=0;i<s.getRow(0).getLastCellNum();i++)s.setColumnWidth(i,24*256);}wb.write(out);return out.toByteArray();
  }catch(IOException e){throw new IllegalStateException("Report export failed",e);}
 }
 private void row(Sheet s,Object... values){Row r=s.createRow(s.getPhysicalNumberOfRows());for(int i=0;i<values.length;i++){Cell c=r.createCell(i);Object v=values[i];if(v instanceof Long)c.setCellValue(v.toString());else if(v instanceof Number)c.setCellValue(((Number)v).doubleValue());else c.setCellValue(v==null?"":v.toString());}}
}
