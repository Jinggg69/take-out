package com.junzhou.test;

import com.junzhou.entity.StoreSettings;
import com.junzhou.exception.OrderBusinessException;
import com.junzhou.mapper.StoreSettingsMapper;
import com.junzhou.service.StoreSettingsService;
import com.junzhou.context.RequestScope;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.data.redis.core.RedisTemplate;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StoreSettingsTest {
    @Test void openingHoursCoverMidnightAndBoundaries() {
        StoreSettings settings = valid();
        settings.setOpensAt("22:00"); settings.setClosesAt("02:00");
        assertTrue(StoreSettingsService.withinHours(settings,LocalTime.of(22,0)));
        assertTrue(StoreSettingsService.withinHours(settings,LocalTime.of(1,59)));
        assertFalse(StoreSettingsService.withinHours(settings,LocalTime.of(2,0)));
        assertFalse(StoreSettingsService.withinHours(settings,LocalTime.NOON));
        settings.setOpensAt("09:00"); settings.setClosesAt("17:00");
        assertTrue(StoreSettingsService.withinHours(settings,LocalTime.of(9,0)));
        assertFalse(StoreSettingsService.withinHours(settings,LocalTime.of(17,0)));
        settings.setClosesAt("09:00");
        assertTrue(StoreSettingsService.withinHours(settings,LocalTime.MIDNIGHT));
    }

    @Test void staleSettingsAndInvalidMoneyCannotOverwriteCurrentConfiguration() {
        StoreSettingsMapper mapper = mock(StoreSettingsMapper.class);
        StoreSettingsService service = new StoreSettingsService();
        ReflectionTestUtils.setField(service,"mapper",mapper);
        RedisTemplate redis = mock(RedisTemplate.class,RETURNS_DEEP_STUBS);
        when(redis.opsForValue().get(anyString())).thenReturn(null);
        ReflectionTestUtils.setField(service,"redisTemplate",redis);
        StoreSettings current = valid(); current.setVersion(4L);
        when(mapper.current()).thenReturn(current); when(mapper.lockStore()).thenReturn(1L);
        RequestScope.set(new RequestScope(null,42L,1L,1L,Collections.emptySet()));
        try {
            assertThrows(OrderBusinessException.class, () -> service.save(valid()));
            StoreSettings invalid = valid(); invalid.setDeliveryFee(new BigDecimal("0.001"));
            assertThrows(OrderBusinessException.class, () -> service.save(invalid));
            verify(mapper,never()).save(any());
            StoreSettings update = valid(); update.setVersion(4L);
            service.save(update);
            assertEquals(5L,update.getVersion());
            verify(mapper).save(update);
        } finally { RequestScope.clear(); }
    }

    private StoreSettings valid() {
        StoreSettings settings = new StoreSettings();
        settings.setId(1L); settings.setVersion(0L); settings.setAddress("测试门店");
        settings.setLatitude(BigDecimal.ZERO); settings.setLongitude(BigDecimal.ZERO);
        settings.setOpensAt("00:00"); settings.setClosesAt("00:00"); settings.setDeliveryRadiusMeters(5000);
        settings.setMinimumOrderAmount(BigDecimal.ZERO); settings.setPackFeePerItem(BigDecimal.ONE);
        settings.setDeliveryFee(BigDecimal.TEN);
        return settings;
    }
}
