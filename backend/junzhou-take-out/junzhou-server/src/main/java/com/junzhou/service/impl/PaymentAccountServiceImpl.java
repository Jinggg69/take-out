package com.junzhou.service.impl;

import com.junzhou.context.RequestScope;
import com.junzhou.entity.*;
import com.junzhou.mapper.PaymentAccountMapper;
import com.junzhou.payment.VerifiedNotification;
import com.junzhou.properties.WeChatProperties;
import com.junzhou.service.PaymentAccountService;
import com.junzhou.utils.WeChatPayUtil;
import org.springframework.stereotype.Service;

@Service
public class PaymentAccountServiceImpl implements PaymentAccountService {
    private final PaymentAccountMapper mapper;
    private final WeChatProperties configuration;
    private final WeChatPayUtil legacyProvider;
    public PaymentAccountServiceImpl(PaymentAccountMapper mapper,WeChatProperties configuration,WeChatPayUtil legacyProvider) {
        this.mapper=mapper; this.configuration=configuration; this.legacyProvider=legacyProvider;
    }
    /** {@inheritDoc} */
    @Override public String currentAccountId() {
        PaymentAccount account=mapper.current();
        validate(account);
        return account.getId();
    }
    /** {@inheritDoc} */
    @Override public WeChatProperties properties(Orders order) {
        if(order==null || order.getPaymentAccountId()==null) throw new IllegalStateException("Order has no payment account");
        PaymentAccount account=mapper.find(order.getPaymentAccountId());
        if(account==null || !RequestScope.requireTenantId().equals(account.getTenantId())
                || !RequestScope.requireStoreId().equals(account.getStoreId())
                || !account.getTenantId().equals(order.getTenantId()) || !account.getStoreId().equals(order.getStoreId()))
            throw new SecurityException("Payment account ownership mismatch");
        return validate(account);
    }
    /** {@inheritDoc} */
    @Override public WeChatPayUtil provider(Orders order) {
        WeChatProperties properties=properties(order);
        return "legacy".equals(order.getPaymentAccountId()) ? legacyProvider : new WeChatPayUtil(properties);
    }
    /** {@inheritDoc} */
    @Override public Orders locate(VerifiedNotification notice) {
        Orders order=mapper.locate(notice.getResource().getString("out_trade_no"),notice.getPaymentAccountId());
        if(order==null) throw new SecurityException("Notification account does not own order");
        return order;
    }
    private WeChatProperties validate(PaymentAccount account) {
        if(account==null) throw new IllegalStateException("Store payment account is missing");
        WeChatProperties properties="legacy".equals(account.getId()) ? configuration : configuration.getAccounts().get(account.getId());
        if(properties==null || account.getMchid()==null || account.getAppid()==null
                || !account.getMchid().equals(properties.getMchid()) || !account.getAppid().equals(properties.getAppid()))
            throw new IllegalStateException("Payment account identity changed or configuration missing");
        return properties;
    }
}
