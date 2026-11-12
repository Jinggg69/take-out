package com.junzhou.payment;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import javax.annotation.PostConstruct;
import java.util.Arrays;

/** Mock mode must be explicitly selected inside an entirely non-production profile set. */
@Component
public class PaymentChannelSettings {
    private final Environment environment;
    public PaymentChannelSettings(Environment environment) { this.environment=environment; }
    public String newOrderChannel() {
        String value=environment.getProperty("sky.payment.channel","wechat");
        if(!"wechat".equals(value) && !"mock".equals(value)) throw new IllegalStateException("Unknown payment channel");
        if("mock".equals(value)) { requireMock(); return "MOCK"; }
        return "WECHAT";
    }
    public boolean mockEnabled() {
        String[] profiles=environment.getActiveProfiles();
        return "mock".equals(environment.getProperty("sky.payment.channel","wechat")) && profiles.length>0
                && Arrays.stream(profiles).allMatch(p->Arrays.asList("dev","demo","test").contains(p));
    }
    public void requireMock() { if(!mockEnabled()) throw new SecurityException("Mock payment is disabled"); }
    @PostConstruct public void validate() {
        try { newOrderChannel(); }
        catch(SecurityException ex) { throw new IllegalStateException("Mock payment requires dev/demo/test profiles only"); }
    }
}
