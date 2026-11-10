package com.junzhou.service.impl;

import com.junzhou.context.RequestScope;
import com.junzhou.exception.OrderBusinessException;
import com.junzhou.mapper.OrderMapper;
import com.junzhou.service.PickupService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
public class PickupServiceImpl implements PickupService {
    private final OrderMapper orders;
    public PickupServiceImpl(OrderMapper orders) { this.orders=orders; }
    /** 校验店员身份后执行受门店和状态限制的条件更新。 */
    @Override @Transactional
    public void ready(Long id) {
        RequestScope.requireEmployeeTenantId();
        if (id==null || id<=0 || orders.pickupReady(id)!=1)
            throw new OrderBusinessException("订单无法标为待取，请刷新订单状态");
    }
    /** 条件更新保证并发核销仅一次成功，重复请求不改写审计字段。 */
    @Override @Transactional
    public void verify(Long id,String credential) {
        RequestScope.requireEmployeeTenantId();
        if (id==null || id<=0 || credential==null || !credential.matches("[0-9a-f]{32}")
                || orders.pickupVerify(id,credential,RequestScope.current().getEmployeeId(),LocalDateTime.now())!=1)
            throw new OrderBusinessException("核销失败，请核对凭证、门店及订单支付状态");
    }
}
