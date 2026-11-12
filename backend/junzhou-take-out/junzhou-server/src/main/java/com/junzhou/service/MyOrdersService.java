package com.junzhou.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.junzhou.context.RequestScope;
import com.junzhou.entity.OrderDetail;
import com.junzhou.exception.MiniappApiException;
import com.junzhou.mapper.MyOrdersMapper;
import com.junzhou.result.PageResult;
import com.junzhou.vo.*;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Customer history is scoped to the signed-in user and the required selected merchant. */
@Service
public class MyOrdersService {
    private final MyOrdersMapper mapper;
    private static final DateTimeFormatter TIME=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public MyOrdersService(MyOrdersMapper mapper) { this.mapper=mapper; }

    public PageResult list(Long merchantId, Long storeId, Integer status, Boolean refundOnly, Integer page, Integer pageSize) {
        requireMerchant(merchantId);
        int p=page==null?1:page, size=pageSize==null?10:pageSize;
        if(p<1 || size<1 || size>50 || (status!=null && (status<1 || status>6))) bad("分页或订单状态参数错误");
        if(storeId!=null && (storeId<=0 || mapper.ownedStore(merchantId,storeId)==0)) bad("门店不属于当前商家");
        long total=mapper.count(merchantId,storeId,status,refundOnly);
        List<MyOrderSummary> records=new ArrayList<>();
        if(total>0) for(MyOrdersMapper.Row row:mapper.list(merchantId,storeId,status,refundOnly,(p-1L)*size,size)) {
            List<OrderDetail> details=mapper.details(merchantId,row.getId());
            MyOrderSummary result=new MyOrderSummary();fill(result,row,details);records.add(result);
        }
        return new PageResult(total,records);
    }

    public MyOrderDetail detail(Long merchantId, Long orderId) {
        requireMerchant(merchantId);
        if(orderId==null || orderId<=0) bad("订单参数错误");
        MyOrdersMapper.Row row=mapper.detail(merchantId,orderId);
        if(row==null) throw new MiniappApiException(404,"订单不存在");
        List<OrderDetail> details=mapper.details(merchantId,orderId);
        MyOrderDetail result=new MyOrderDetail();fill(result,row,details);
        List<MyOrderDetail.DetailItem> items=new ArrayList<>();BigDecimal goods=BigDecimal.ZERO;
        for(OrderDetail d:details) {
            MyOrderDetail.DetailItem item=new MyOrderDetail.DetailItem();item.setName(d.getName());item.setImage(d.getImage());
            item.setNumber(d.getNumber());item.setUnitPrice(money(d.getAmount()));item.setDishFlavor(d.getDishFlavor());items.add(item);
            if(d.getAmount()!=null && d.getNumber()!=null) goods=goods.add(d.getAmount().multiply(BigDecimal.valueOf(d.getNumber())));
        }
        result.setOrderDetails(items);
        JSONObject pricing=snapshot(row.getPricingSnapshot()), store=snapshot(row.getStoreSnapshot());
        result.setGoodsAmount(money(first(decimal(pricing,"goodsAmount"),goods)));
        BigDecimal historicalPackFee=row.getHistoricalPackAmount()==null?null:BigDecimal.valueOf(row.getHistoricalPackAmount());
        result.setPackFee(money(first(decimal(pricing,"packFee"),historicalPackFee)));
        result.setDeliveryFee(money(first(decimal(pricing,"deliveryFee"),decimal(store,"deliveryFee"))));
        String type=row.getFulfillmentType();boolean delivery=type==null || "DELIVERY".equals(type);
        result.setContactName(delivery?text(row.getContactName(),row.getConsignee()):row.getContactName());
        result.setContactPhone(delivery?text(row.getContactPhone(),row.getPhone()):row.getContactPhone());
        result.setDeliveryAddress(delivery?row.getAddress():null);result.setRemark(row.getRemark());
        result.setPickupNumber("PICKUP".equals(type)?row.getPickupNumber():null);
        result.setTableName("DINE_IN".equals(type)?row.getTableSnapshot():null);
        result.setCancelReason(row.getCancelReason());result.setRejectionReason(row.getRejectionReason());
        result.setEstimatedDeliveryTime(delivery?time(row.getEstimatedDeliveryTime()):null);
        return result;
    }

    public MyOrderFilters filters(Long merchantId) {
        MyOrdersMapper.Merchant merchant=requireMerchant(merchantId);
        List<MyOrdersMapper.Row> rows=mapper.filterStores(merchantId);
        MyOrderFilters result=new MyOrderFilters();result.setMerchantId(merchantId.toString());
        result.setMerchantName(text(merchant.getName(),"历史商家"));
        List<MyOrderFilters.StoreOption> stores=new ArrayList<>();MyOrdersMapper.Row latest=null;
        for(MyOrdersMapper.Row row:rows) {
            MyOrderFilters.StoreOption store=new MyOrderFilters.StoreOption();store.setStoreId(row.getStoreId().toString());
            store.setStoreName(storeName(row));stores.add(store);
            if(latest==null || compare(row,latest)>0) latest=row;
        }
        if(latest!=null) result.setMerchantName(merchantName(latest));
        result.setStores(stores);return result;
    }

    private MyOrdersMapper.Merchant requireMerchant(Long merchantId) {
        RequestScope.requireAuthenticatedUserId();
        if(merchantId==null || merchantId<=0) bad("请选择商家");
        MyOrdersMapper.Merchant merchant=mapper.merchant(merchantId);
        if(merchant==null) throw new MiniappApiException(404,"商家不可用");
        return merchant;
    }
    private void fill(MyOrderSummary result,MyOrdersMapper.Row row,List<OrderDetail> details) {
        result.setOrderId(row.getId().toString());result.setOrderNumber(row.getNumber());
        result.setMerchantId(row.getTenantId().toString());result.setMerchantName(merchantName(row));
        result.setStoreId(row.getStoreId().toString());result.setStoreName(storeName(row));
        result.setStoreAvailable(Integer.valueOf(1).equals(row.getCurrentMerchantStatus()) && Integer.valueOf(1).equals(row.getCurrentStoreStatus()));
        result.setOrderTime(time(row.getOrderTime()));result.setStatus(row.getStatus());result.setPayStatus(row.getPayStatus());
        result.setRefundStatus(row.getRefundStatus());result.setFulfillmentType(row.getFulfillmentType());result.setFulfillmentStatus(row.getFulfillmentStatus());
        result.setAmount(money(row.getAmount()));result.setClientRequestId(row.getClientRequestId());
        List<MyOrderSummary.Item> items=new ArrayList<>();int total=0;
        for(OrderDetail detail:details) {
            MyOrderSummary.Item item=new MyOrderSummary.Item();item.setName(detail.getName());item.setImage(detail.getImage());item.setNumber(detail.getNumber());
            if(detail.getNumber()!=null)total+=detail.getNumber();items.add(item);
        }
        result.setTotalQuantity(total);result.setItems(items);
    }
    private String merchantName(MyOrdersMapper.Row row) {return text(snapshot(row.getStoreSnapshot()).getString("merchantName"),row.getCurrentMerchantName(),"历史商家");}
    private String storeName(MyOrdersMapper.Row row) {
        JSONObject s=snapshot(row.getStoreSnapshot());return text(s.getString("storeName"),s.getString("name"),row.getCurrentStoreName(),"历史门店");
    }
    private static int compare(MyOrdersMapper.Row a,MyOrdersMapper.Row b) {
        int time=Comparator.nullsFirst(Comparator.<LocalDateTime>naturalOrder()).compare(a.getOrderTime(),b.getOrderTime());
        return time!=0?time:a.getId().compareTo(b.getId());
    }
    private static JSONObject snapshot(String raw) {
        if(raw==null || raw.trim().isEmpty())return new JSONObject();
        try {Object value=JSON.parse(raw);return value instanceof JSONObject?(JSONObject)value:new JSONObject();}
        catch(com.alibaba.fastjson.JSONException ex){return new JSONObject();}
    }
    private static BigDecimal decimal(JSONObject value,String key) {
        Object raw=value.get(key);
        if(!(raw instanceof Number) && !(raw instanceof String))return null;
        try{return new BigDecimal(raw.toString());}catch(NumberFormatException ex){return null;}
    }
    private static BigDecimal first(BigDecimal a,BigDecimal b){return a==null?b:a;}
    private static String text(String... values){for(String value:values)if(value!=null && !value.trim().isEmpty())return value;return null;}
    private static String money(BigDecimal value){return value==null?null:value.setScale(2,RoundingMode.HALF_UP).toPlainString();}
    private static String time(LocalDateTime value){return value==null?null:TIME.format(value);}
    private static void bad(String message){throw new MiniappApiException(400,message);}
}
