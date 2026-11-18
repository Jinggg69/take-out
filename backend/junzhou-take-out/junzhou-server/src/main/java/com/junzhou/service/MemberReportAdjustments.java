package com.junzhou.service;

import com.junzhou.context.RequestScope;
import com.junzhou.report.ReportFilter;
import com.junzhou.report.ReportRow;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.*;

/** Supplemental member ledger projections keep legacy report SQL and legacy orders compatible. */
@Service
public class MemberReportAdjustments {
 private final JdbcTemplate db;
 public MemberReportAdjustments(JdbcTemplate db){this.db=db;}
 private String scope(ReportFilter f,List<Object> args){
  if(!Objects.equals(f.getTenant(),RequestScope.requireEmployeeTenantId()) || f.getStores().isEmpty() || !RequestScope.current().getAllowedStoreIds().containsAll(f.getStores())) throw new SecurityException("Invalid membership report scope");
  args.add(f.getTenant());args.addAll(f.getStores());
  return "a.tenant_id=? and a.store_id in ("+String.join(",",Collections.nCopies(f.getStores().size(),"?"))+")";
 }
 public void daily(ReportFilter f,SortedMap<String,ReportRow> days){
  List<Object> args=new ArrayList<>();String where=scope(f,args);args.add(f.getFrom());args.add(f.getUntil());
  db.query("select cast(o.checkout_time as date) as report_day,sum(a.gift) amount from member_order_asset a join orders o on o.id=a.order_id and o.tenant_id=a.tenant_id and o.store_id=a.store_id where "+where+" and o.checkout_time>=? and o.checkout_time<? group by cast(o.checkout_time as date)",rs->{ReportRow r=days.get(rs.getDate("report_day").toString());if(r!=null)r.setReceived(r.getReceived().subtract(rs.getBigDecimal("amount").movePointLeft(2)));},args.toArray());
  args=new ArrayList<>();where=scope(f,args);args.add(f.getFrom());args.add(f.getUntil());
  db.query("select cast(a.refunded_at as date) as report_day,sum(a.principal) amount from member_order_asset a where "+where+" and a.status='REFUNDED' and a.refunded_at>=? and a.refunded_at<? group by cast(a.refunded_at as date)",rs->{ReportRow r=days.get(rs.getDate("report_day").toString());if(r!=null)r.setRefunded(r.getRefunded().add(rs.getBigDecimal("amount").movePointLeft(2)));},args.toArray());
 }
 public List<ReportRow> sales(ReportFilter f,List<ReportRow> rows){
  List<Object> args=new ArrayList<>();String where=scope(f,args);args.add(f.getFrom());args.add(f.getUntil());args.add(f.getUntil());args.add(f.getUntil());
  Map<String,Long> subtract=new HashMap<>();
  db.query("select d.dish_id,d.setmeal_id,sum(d.number) quantity from member_order_asset a join orders o on o.id=a.order_id and o.tenant_id=a.tenant_id join order_detail d on d.order_id=o.id where "+where+" and o.checkout_time>=? and o.checkout_time<? and a.status='REFUNDED' and a.refunded_at<? and not exists(select 1 from refund_request r where r.order_id=o.id and r.status='SUCCESS' and r.updated_at<?) group by d.dish_id,d.setmeal_id",rs->{subtract.put(rs.getString("dish_id")+":"+rs.getString("setmeal_id"),rs.getLong("quantity"));},args.toArray());
  List<ReportRow> result=new ArrayList<>();for(ReportRow row:rows){row.setQuantity(Math.max(0,row.getQuantity()-subtract.getOrDefault(row.getDishId()+":"+row.getSetmealId(),0L)));if(row.getQuantity()>0)result.add(row);}result.sort(Comparator.comparingLong(ReportRow::getQuantity).reversed());return result;
 }
 public void export(ReportFilter f,List<ReportRow> ledger,List<ReportRow> items){
  List<Object> args=new ArrayList<>();String where=scope(f,args);args.add(f.getUntil());
  Map<Long,Map<String,Object>> assets=new HashMap<>();
  db.query("select a.order_id,a.principal,a.gift,a.refunded_at,o.number,o.store_id,o.fulfillment_type from member_order_asset a join orders o on o.id=a.order_id and o.tenant_id=a.tenant_id where "+where+" and o.order_time<?",rs->{Map<String,Object> a=new HashMap<>();a.put("principal",rs.getBigDecimal("principal").movePointLeft(2));a.put("gift",rs.getBigDecimal("gift").movePointLeft(2));a.put("refundedAt",rs.getTimestamp("refunded_at"));a.put("number",rs.getString("number"));a.put("store",rs.getLong("store_id"));a.put("fulfillment",rs.getString("fulfillment_type"));assets.put(rs.getLong("order_id"),a);},args.toArray());
  Set<Long> existingRefunds=new HashSet<>();List<ReportRow> split=new ArrayList<>();
  for(ReportRow row:ledger){Map<String,Object>a=assets.get(row.getId());if(a==null)continue;BigDecimal principal=(BigDecimal)a.get("principal"),gift=(BigDecimal)a.get("gift");
   if("PAYMENT".equals(row.getEvent())){BigDecimal external=row.getAmount().subtract(principal).subtract(gift);row.setAmount(row.getAmount().subtract(gift));split.add(copy(row,"EXTERNAL_PAYMENT",external));split.add(copy(row,"WALLET_PRINCIPAL",principal));split.add(copy(row,"WALLET_GIFT",gift));}
   if("REFUND".equals(row.getEvent())){existingRefunds.add(row.getId());row.setAmount(row.getAmount().add(principal));}
  }
  for(Map.Entry<Long,Map<String,Object>> entry:assets.entrySet()){
   Map<String,Object>a=entry.getValue();Timestamp at=(Timestamp)a.get("refundedAt");if(at==null || !at.toLocalDateTime().isBefore(f.getUntil()))continue;
   for(ReportRow row:items)if(entry.getKey().equals(row.getId()))row.setEvent("REFUNDED");
   if(at.toLocalDateTime().isBefore(f.getFrom()) || existingRefunds.contains(entry.getKey()))continue;
   ReportRow refund=new ReportRow();refund.setId(entry.getKey());refund.setNumber((String)a.get("number"));refund.setStoreId((Long)a.get("store"));refund.setFulfillmentType((String)a.get("fulfillment"));refund.setDay(at.toLocalDateTime().toString());refund.setEvent("REFUND");refund.setAmount((BigDecimal)a.get("principal"));split.add(refund);
  }
  ledger.addAll(split);ledger.sort(Comparator.comparing(ReportRow::getId).thenComparing(ReportRow::getEvent));
 }
 private ReportRow copy(ReportRow source,String event,BigDecimal amount){ReportRow r=new ReportRow();r.setId(source.getId());r.setNumber(source.getNumber());r.setStoreId(source.getStoreId());r.setFulfillmentType(source.getFulfillmentType());r.setDay(source.getDay());r.setEvent(event);r.setAmount(amount);return r;}
}
