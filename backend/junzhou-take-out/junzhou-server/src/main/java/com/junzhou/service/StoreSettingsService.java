package com.junzhou.service;

import com.junzhou.entity.StoreSettings;
import com.junzhou.exception.OrderBusinessException;
import com.junzhou.mapper.StoreSettingsMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class StoreSettingsService {
    @Autowired private StoreSettingsMapper mapper;
    @Autowired private RedisTemplate<String,Object> redisTemplate;

    public StoreSettings current() {
        StoreSettings settings = mapper.current();
        if (settings == null) throw new OrderBusinessException("门店已停用，请重新选店");
        settings.setOpen(isOpen(settings));
        return settings;
    }

    public List<StoreSettings> stores() {
        List<StoreSettings> stores = mapper.stores();
        for (StoreSettings store : stores) store.setOpen(isOpen(store));
        return stores;
    }

    public boolean isOpen(StoreSettings settings) {
        String key = "junzhou:v2:tenant:" + com.junzhou.context.RequestScope.requireTenantId()
                + ":store:" + settings.getId() + ":shop-status";
        Object status = redisTemplate.opsForValue().get(key);
        return (status == null || Integer.valueOf(1).equals(status)) && withinHours(settings, LocalTime.now(ZoneId.of("Asia/Shanghai")));
    }

    public static boolean withinHours(StoreSettings settings, LocalTime now) {
        LocalTime start = LocalTime.parse(settings.getOpensAt());
        LocalTime end = LocalTime.parse(settings.getClosesAt());
        if (start.equals(end)) return true;
        if (start.isBefore(end)) return !now.isBefore(start) && now.isBefore(end);
        return !now.isBefore(start) || now.isBefore(end);
    }

    public StoreSettings requireOpen() {
        StoreSettings settings = current();
        if (!settings.getOpen()) throw new OrderBusinessException("门店当前休息，请在营业时间下单");
        return settings;
    }

    public void lockVersion(Long version) {
        if (mapper.lockStore() == null || !current().getVersion().equals(version))
            throw new OrderBusinessException("门店配置已变化，请重新确认订单");
    }

    @Transactional
    public void save(StoreSettings settings) {
        validate(settings);
        lockVersion(settings.getVersion());
        settings.setVersion(settings.getVersion() + 1);
        mapper.save(settings);
    }

    private void validate(StoreSettings s) {
        if (s == null || s.getVersion() == null || s.getVersion() < 0 || s.getVersion() == Long.MAX_VALUE
                || s.getAddress() == null || s.getAddress().trim().isEmpty() || s.getAddress().length() > 255
                || s.getLatitude() == null || s.getLatitude().abs().compareTo(new BigDecimal("90")) > 0
                || s.getLongitude() == null || s.getLongitude().abs().compareTo(new BigDecimal("180")) > 0
                || s.getLatitude().scale() > 6 || s.getLongitude().scale() > 6
                || s.getDeliveryRadiusMeters() == null || s.getDeliveryRadiusMeters() <= 0 || s.getDeliveryRadiusMeters() > 100000) {
            throw new OrderBusinessException("请填写有效的门店地址、坐标及配送范围");
        }
        try {
            if (!s.getOpensAt().matches("[0-2][0-9]:[0-5][0-9]") || !s.getClosesAt().matches("[0-2][0-9]:[0-5][0-9]")) throw new IllegalArgumentException();
            LocalTime.parse(s.getOpensAt()); LocalTime.parse(s.getClosesAt());
        } catch (RuntimeException ex) { throw new OrderBusinessException("请填写有效的营业时间"); }
        money(s.getMinimumOrderAmount()); money(s.getPackFeePerItem()); money(s.getDeliveryFee());
        if (s.getFreeDeliveryThreshold() != null) money(s.getFreeDeliveryThreshold());
        if (s.getContactPhone() != null) {
            String phone = s.getContactPhone().trim();
            if (!phone.isEmpty() && (phone.length() > 32 || !phone.matches("[+0-9() -]+") || !phone.matches(".*[0-9].*")))
                throw new OrderBusinessException("请填写有效的门店联系电话，最多32个字符");
            s.setContactPhone(phone.isEmpty() ? null : phone);
        }
    }

    private void money(BigDecimal value) {
        if (value == null || value.signum() < 0 || value.scale() > 2 || value.compareTo(new BigDecimal("99999999.99")) > 0)
            throw new OrderBusinessException("费用须为非负金额，最多两位小数");
    }
}
