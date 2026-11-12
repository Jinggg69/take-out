package com.junzhou.test;

import com.junzhou.payment.PaymentChannelSettings;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;

class PaymentChannelSettingsTest {
    @Test void defaultIsRealAndMockRequiresAnExplicitNonProductionProfileSet() {
        MockEnvironment env=new MockEnvironment();
        PaymentChannelSettings settings=new PaymentChannelSettings(env);
        assertEquals("WECHAT",settings.newOrderChannel());
        env.setProperty("sky.payment.channel","mock");
        assertThrows(IllegalStateException.class,settings::validate);
        for(String profile:new String[]{"dev","demo","test"}) {
            env.setActiveProfiles(profile); settings.validate(); assertEquals("MOCK",settings.newOrderChannel());
        }
        env.setActiveProfiles("dev","prod");
        assertThrows(IllegalStateException.class,settings::validate);
        assertThrows(SecurityException.class,settings::requireMock);
        env.setProperty("sky.payment.channel","typo");
        assertThrows(IllegalStateException.class,settings::validate);
    }
}
