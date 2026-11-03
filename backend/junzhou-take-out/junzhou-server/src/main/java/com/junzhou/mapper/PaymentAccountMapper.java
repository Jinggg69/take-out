package com.junzhou.mapper;

import com.junzhou.entity.PaymentAccount;
import com.junzhou.entity.Orders;
import org.apache.ibatis.annotations.*;

/** Account identities are global configuration; business orders remain scoped. */
@Mapper
public interface PaymentAccountMapper {
    @Select("select * from payment_account where id=#{id}")
    PaymentAccount find(String id);

    @Select("<script><bind name='tenant' value='@com.junzhou.context.RequestScope@requireTenantId()'/><bind name='store' value='@com.junzhou.context.RequestScope@requireStoreId()'/>select a.* from payment_account a join store_payment_account s on s.payment_account_id=a.id and s.tenant_id=a.tenant_id and s.store_id=a.store_id where s.tenant_id=#{tenant} and s.store_id=#{store} for update</script>")
    PaymentAccount current();

    // Only called after cryptographic verification. No externally supplied tenant/store is accepted.
    @Select("select o.* from orders o join payment_account a on a.id=o.payment_account_id and a.tenant_id=o.tenant_id and a.store_id=o.store_id join store s on s.id=o.store_id and s.tenant_id=o.tenant_id join tenant t on t.id=o.tenant_id where o.number=#{number} and a.id=#{account}")
    Orders locate(@Param("number") String number,@Param("account") String account);
}
