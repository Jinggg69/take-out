package com.junzhou.mapper;

import com.junzhou.dto.MerchantSummaryDTO;
import org.apache.ibatis.annotations.*;
import java.util.List;

/** Controlled public directory queries, independent of scoped business mappers. */
@Mapper
public interface MerchantDiscoveryMapper {
    String ACTIVE = " t.status=1 AND EXISTS(SELECT 1 FROM store a WHERE a.tenant_id=t.id AND a.status=1) ";
    String MERCHANT_SEARCH = "<if test='pattern != null'> AND t.name LIKE #{pattern} ESCAPE '!'</if>";
    String STORE_SEARCH = "<if test='pattern != null'> AND s.name LIKE #{pattern} ESCAPE '!'</if>";
    String MERCHANT_COLUMNS = "t.id merchant_id,t.name merchant_name,t.logo_url,t.introduction,"
            + "(SELECT COUNT(*) FROM store a WHERE a.tenant_id=t.id AND a.status=1) active_store_count";
    @Select("<script>SELECT COUNT(*) FROM tenant t WHERE " + ACTIVE + MERCHANT_SEARCH + "</script>")
    long countMerchants(@Param("pattern") String pattern);
    @Select("<script>SELECT " + MERCHANT_COLUMNS + " FROM tenant t WHERE " + ACTIVE + MERCHANT_SEARCH
            + " ORDER BY t.id LIMIT #{limit} OFFSET #{offset}</script>")
    List<MerchantSummaryDTO> merchants(@Param("pattern") String pattern,@Param("offset") long offset,@Param("limit") int limit);
    @Select("SELECT " + MERCHANT_COLUMNS + " FROM tenant t WHERE t.id=#{id} AND " + ACTIVE)
    MerchantSummaryDTO merchant(@Param("id") Long id);
    @Select("<script>SELECT COUNT(*) FROM store s JOIN tenant t ON t.id=s.tenant_id WHERE t.id=#{merchantId} AND t.status=1 AND s.status=1 " + STORE_SEARCH + "</script>")
    long countStores(@Param("merchantId") Long merchantId,@Param("pattern") String pattern);
    String STORE_COLUMNS = StoreSettingsMapper.COLUMNS + ",s.tenant_id merchant_id,t.name merchant_name,t.logo_url";
    @Select("<script>SELECT " + STORE_COLUMNS + StoreSettingsMapper.FROM
            + " WHERE t.id=#{merchantId} AND t.status=1 AND s.status=1 " + STORE_SEARCH + " ORDER BY s.id LIMIT #{limit} OFFSET #{offset}</script>")
    List<DiscoveryStoreRow> stores(@Param("merchantId") Long merchantId,@Param("pattern") String pattern,@Param("offset") long offset,@Param("limit") int limit);
    @Select("SELECT " + STORE_COLUMNS + StoreSettingsMapper.FROM + " WHERE s.id=#{id} AND s.status=1 AND t.status=1")
    DiscoveryStoreRow store(@Param("id") Long id);
}
