package com.junzhou.service;
import com.junzhou.entity.Orders;
import com.junzhou.mapper.OrderMapper;
import com.junzhou.mapper.OrderMaintenanceMapper;
import com.junzhou.membership.MembershipCheckoutService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
/** Execute lifecycle changes and account effects under one order lock and transaction. */
@Service public class MemberOrderMaintenance {
 private final OrderMapper orders;private final OrderMaintenanceMapper maintenance;private final MembershipCheckoutService members;
 public MemberOrderMaintenance(OrderMapper orders,OrderMaintenanceMapper maintenance,MembershipCheckoutService members){this.orders=orders;this.maintenance=maintenance;this.members=members;}
 @Transactional public void process(Long id,boolean timeout,LocalDateTime cutoff,LocalDateTime now){
  Orders order=orders.getByIdForUpdate(id);if(order==null)return;
  int changed=timeout?maintenance.cancelOrderBefore(id,cutoff,now):maintenance.completeOrderBefore(id,cutoff,now);
  if(changed==1){if(timeout)members.release(order);else members.complete(order);}
 }
}
