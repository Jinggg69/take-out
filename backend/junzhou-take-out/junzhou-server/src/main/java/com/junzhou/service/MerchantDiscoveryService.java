package com.junzhou.service;

import com.junzhou.dto.*;
import com.junzhou.exception.MiniappApiException;
import com.junzhou.mapper.*;
import com.junzhou.result.PageResult;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
public class MerchantDiscoveryService {
    private final MerchantDiscoveryMapper mapper;
    private final RedisTemplate<String,Object> redis;
    public MerchantDiscoveryService(MerchantDiscoveryMapper mapper, RedisTemplate<String,Object> redis) {
        this.mapper = mapper; this.redis = redis;
    }

    public PageResult merchants(String keyword, Integer page, Integer pageSize) {
        long offset = offset(page,pageSize); String pattern = pattern(keyword);
        long total = mapper.countMerchants(pattern);
        List<MerchantSummaryDTO> records = mapper.merchants(pattern,offset,size(pageSize));
        for(MerchantSummaryDTO record:records) record.setLogoUrl(publicLogo(record.getLogoUrl()));
        return new PageResult(total,records);
    }

    public MerchantSummaryDTO merchant(String id) {
        MerchantSummaryDTO record = mapper.merchant(id(id));
        if(record == null) throw new MiniappApiException(404,"商家不存在或已停用");
        record.setLogoUrl(publicLogo(record.getLogoUrl()));
        return record;
    }

    public PageResult stores(String merchantId,String keyword,Integer page,Integer pageSize) {
        long offset = offset(page,pageSize); String pattern = pattern(keyword);
        Long merchant = id(merchantId);
        // Validate once; a concurrent last-store deactivation then yields an empty list.
        if(mapper.merchant(merchant) == null) throw new MiniappApiException(404,"商家不存在或已停用");
        long total = mapper.countStores(merchant,pattern);
        List<StoreSummaryDTO> records = new ArrayList<>();
        for(DiscoveryStoreRow row:mapper.stores(merchant,pattern,offset,size(pageSize))) {
            StoreSummaryDTO dto = new StoreSummaryDTO();
            dto.setStoreId(row.getId().toString()); dto.setMerchantId(row.getMerchantId().toString());
            dto.setStoreName(row.getName()); dto.setAddress(row.getAddress());
            dto.setOpen(open(row)); dto.setOpensAt(row.getOpensAt()); dto.setClosesAt(row.getClosesAt());
            records.add(dto);
        }
        return new PageResult(total,records);
    }

    public StoreContextDTO context(String storeId) {
        DiscoveryStoreRow row = mapper.store(id(storeId));
        if(row == null) throw new MiniappApiException(404,"门店不存在或已停用");
        StoreContextDTO dto = new StoreContextDTO();
        dto.setMerchantId(row.getMerchantId().toString()); dto.setMerchantName(row.getMerchantName());
        dto.setLogoUrl(publicLogo(row.getLogoUrl())); dto.setStoreId(row.getId().toString());
        dto.setStoreName(row.getName()); dto.setAddress(row.getAddress()); dto.setContactPhone(row.getContactPhone());
        dto.setLatitude(row.getLatitude()); dto.setLongitude(row.getLongitude()); dto.setOpen(open(row));
        dto.setOpensAt(row.getOpensAt()); dto.setClosesAt(row.getClosesAt());
        dto.setMinimumOrderAmount(money(row.getMinimumOrderAmount())); dto.setPackFeePerItem(money(row.getPackFeePerItem()));
        dto.setDeliveryFee(money(row.getDeliveryFee())); dto.setFreeDeliveryThreshold(money(row.getFreeDeliveryThreshold()));
        dto.setDeliveryRadiusMeters(row.getDeliveryRadiusMeters()); dto.setConfigVersion(row.getVersion().toString());
        return dto;
    }

    private boolean open(DiscoveryStoreRow row) {
        try {
            Object status = redis.opsForValue().get("junzhou:v2:tenant:"+row.getMerchantId()+":store:"+row.getId()+":shop-status");
            return (status == null || Integer.valueOf(1).equals(status))
                    && StoreSettingsService.withinHours(row,LocalTime.now(ZoneId.of("Asia/Shanghai")));
        } catch(org.springframework.dao.DataAccessException ex) {
            throw new MiniappApiException(503,"门店营业状态暂不可用，请稍后重试");
        }
    }
    private static String money(BigDecimal value) { return value == null ? null : value.setScale(2).toPlainString(); }
    private static Long id(String value) {
        try {
            if(value == null || !value.matches("[0-9]+")) throw new NumberFormatException();
            long id = Long.parseLong(value); if(id <= 0) throw new NumberFormatException(); return id;
        } catch(NumberFormatException ex) { throw new MiniappApiException(400,"ID 须为有效正整数"); }
    }
    private static int size(Integer value) { return value == null ? 10 : value; }
    private static long offset(Integer page,Integer pageSize) {
        int p = page == null ? 1 : page; int s = size(pageSize);
        if(p < 1 || s < 1 || s > 50) throw new MiniappApiException(400,"分页参数无效");
        return ((long)p-1)*s;
    }
    private static String pattern(String keyword) {
        if(keyword == null) return null;
        keyword = keyword.trim();
        if(keyword.length() > 100) throw new MiniappApiException(400,"搜索内容不能超过100字");
        return keyword.isEmpty() ? null : "%"+keyword.replace("!","!!").replace("%","!%").replace("_","!_")+"%";
    }
    /** Only explicit public static image URLs; never signed or employee API URLs. */
    private static String publicLogo(String value) {
        if(value == null) return null;
        try {
            URI uri = new URI(value);
            if(!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null) return null;
            String path = uri.getRawPath();
            if(path == null || !path.matches("/public/[A-Za-z0-9/_-]+\\.(?i:png|jpe?g|webp|gif|svg)")) return null;
            return value;
        } catch(java.net.URISyntaxException ex) { return null; }
    }
}
