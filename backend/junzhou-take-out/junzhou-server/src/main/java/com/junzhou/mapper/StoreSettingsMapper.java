package com.junzhou.mapper;

import com.junzhou.entity.StoreSettings;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface StoreSettingsMapper {
    String SCOPE = "<bind name='tenant' value='@com.junzhou.context.RequestScope@requireTenantId()'/>"
            + "<bind name='store' value='@com.junzhou.context.RequestScope@requireStoreId()'/>";
    String COLUMNS = "s.id,s.name,COALESCE(c.version,0) version,COALESCE(c.address,'') address,c.contact_phone,c.latitude,c.longitude,"
            + "COALESCE(c.opens_at,'00:00') opens_at,COALESCE(c.closes_at,'00:00') closes_at,"
            + "COALESCE(c.delivery_radius_meters,5000) delivery_radius_meters,COALESCE(c.minimum_order_amount,0) minimum_order_amount,"
            + "COALESCE(c.pack_fee_per_item,1) pack_fee_per_item,COALESCE(c.delivery_fee,6) delivery_fee,c.free_delivery_threshold";
    String FROM = " FROM store s JOIN tenant t ON t.id=s.tenant_id LEFT JOIN store_settings c ON c.tenant_id=s.tenant_id AND c.store_id=s.id ";

    @Select("<script>" + SCOPE + "SELECT " + COLUMNS + FROM + "WHERE s.tenant_id=#{tenant} AND s.id=#{store} AND s.status=1 AND t.status=1</script>")
    StoreSettings current();

    @Select("<script>" + SCOPE + "SELECT " + COLUMNS + FROM + "WHERE s.tenant_id=#{tenant} AND s.status=1 AND t.status=1 ORDER BY s.id</script>")
    List<StoreSettings> stores();

    @Select("<script>" + SCOPE + "SELECT id FROM store WHERE tenant_id=#{tenant} AND id=#{store} AND status=1 FOR UPDATE</script>")
    Long lockStore();

    @Insert("<script>" + SCOPE + "INSERT INTO store_settings(tenant_id,store_id,version,address,contact_phone,latitude,longitude,opens_at,closes_at,"
            + "delivery_radius_meters,minimum_order_amount,pack_fee_per_item,delivery_fee,free_delivery_threshold) "
            + "VALUES(#{tenant},#{store},#{version},#{address},#{contactPhone},#{latitude},#{longitude},#{opensAt},#{closesAt},#{deliveryRadiusMeters},"
            + "#{minimumOrderAmount},#{packFeePerItem},#{deliveryFee},#{freeDeliveryThreshold}) "
            + "ON DUPLICATE KEY UPDATE version=VALUES(version),address=VALUES(address),contact_phone=VALUES(contact_phone),latitude=VALUES(latitude),longitude=VALUES(longitude),"
            + "opens_at=VALUES(opens_at),closes_at=VALUES(closes_at),delivery_radius_meters=VALUES(delivery_radius_meters),"
            + "minimum_order_amount=VALUES(minimum_order_amount),pack_fee_per_item=VALUES(pack_fee_per_item),"
            + "delivery_fee=VALUES(delivery_fee),free_delivery_threshold=VALUES(free_delivery_threshold)</script>")
    void save(StoreSettings settings);
}
