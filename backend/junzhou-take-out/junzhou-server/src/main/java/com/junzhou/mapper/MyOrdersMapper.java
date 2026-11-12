package com.junzhou.mapper;

import com.junzhou.entity.Orders;
import com.junzhou.entity.OrderDetail;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.ibatis.annotations.*;
import java.util.List;

/** Dedicated identity-only reads. Every order access binds authenticated identity in SQL. */
@Mapper
public interface MyOrdersMapper {
    String USER = "<bind name='authenticatedUser' value='@com.junzhou.context.RequestScope@requireAuthenticatedUserId()'/>";
    String OWN = " o.user_id=#{authenticatedUser} and o.tenant_id=#{merchantId} ";
    String JOINS = " from orders o left join tenant t on t.id=o.tenant_id left join store s on s.id=o.store_id and s.tenant_id=o.tenant_id ";
    String COLUMNS = "select o.id,o.number,o.tenant_id,o.store_id,o.order_time,o.status,o.pay_status,o.refund_status,o.amount,o.store_snapshot,o.pricing_snapshot,o.client_request_id,o.fulfillment_type,o.fulfillment_status,o.pack_amount historical_pack_amount,o.contact_name,o.contact_phone,o.consignee,o.phone,o.address,o.remark,o.pickup_number,o.table_snapshot,o.cancel_reason,o.rejection_reason,o.estimated_delivery_time,t.name current_merchant_name,t.status current_merchant_status,s.name current_store_name,s.status current_store_status ";
    String FILTERS = "<if test='storeId != null'>and o.store_id=#{storeId}</if><if test='status != null'>and o.status=#{status}</if><if test='refundOnly != null and refundOnly'>and (o.pay_status in (2,3,4) or o.refund_status is not null)</if>";

    @Select("<script>"+USER+"select id,name,status from tenant where id=#{merchantId} and (status=1 or exists(select 1 from orders o where "+OWN+"))</script>")
    Merchant merchant(@Param("merchantId") Long merchantId);

    @Select("select count(*) from store where id=#{storeId} and tenant_id=#{merchantId}")
    int ownedStore(@Param("merchantId") Long merchantId,@Param("storeId") Long storeId);

    @Select("<script>"+USER+"select count(*) from orders o where "+OWN+FILTERS+"</script>")
    long count(@Param("merchantId") Long merchantId,@Param("storeId") Long storeId,@Param("status") Integer status,@Param("refundOnly") Boolean refundOnly);

    @Select("<script>"+USER+COLUMNS+JOINS+"where "+OWN+FILTERS+" order by o.order_time desc,o.id desc limit #{limit} offset #{offset}</script>")
    List<Row> list(@Param("merchantId") Long merchantId,@Param("storeId") Long storeId,@Param("status") Integer status,@Param("refundOnly") Boolean refundOnly,@Param("offset") long offset,@Param("limit") int limit);

    @Select("<script>"+USER+COLUMNS+JOINS+"where "+OWN+"and o.id=#{orderId}</script>")
    Row detail(@Param("merchantId") Long merchantId,@Param("orderId") Long orderId);

    @Select("<script>"+USER+"select d.name,d.image,d.number,d.amount,d.dish_flavor from order_detail d join orders o on o.id=d.order_id where "+OWN+" and o.id=#{orderId} order by d.id</script>")
    List<OrderDetail> details(@Param("merchantId") Long merchantId,@Param("orderId") Long orderId);

    @Select("<script>"+USER+COLUMNS+JOINS+"where "+OWN+" and not exists(select 1 from orders newer where newer.user_id=#{authenticatedUser} and newer.tenant_id=#{merchantId} and newer.store_id=o.store_id and (newer.order_time &gt; o.order_time or (newer.order_time=o.order_time and newer.id &gt; o.id))) order by o.store_id</script>")
    List<Row> filterStores(@Param("merchantId") Long merchantId);

    @Data class Merchant { private Long id; private String name; private Integer status; }

    /** Internal projection only, never returned by the controller. No credential columns selected. */
    @Data @EqualsAndHashCode(callSuper=true)
    class Row extends Orders {
        private String currentMerchantName, currentStoreName;
        private Integer currentMerchantStatus, currentStoreStatus;
        // The legacy entity uses primitive int; this projection must retain an unknown SQL NULL.
        private Integer historicalPackAmount;
    }
}
