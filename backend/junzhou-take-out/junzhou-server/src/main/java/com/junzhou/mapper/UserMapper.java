package com.junzhou.mapper;


import com.junzhou.entity.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper {
    @Select("<script><bind name='_user' value='@com.junzhou.context.RequestScope@requireUserId()'/> select id from `user` where id=#{id} and id=#{_user} for update</script>")
    Long lockById(Long id);

    /**
     * 认证引导专用：按微信服务已验证的 openid 查询全局用户身份
     * @param openid
     * @return
     */
    @Select("select * from user where openid = #{openid}")
    User getByOpenid(String openid);

    /**
     * 根据用户 ID 查询微信支付所需的 openid。
     */
    @Select("<script><bind name='_user' value='@com.junzhou.context.RequestScope@requireUserId()'/> select * from user where id = #{id} and id=#{_user}</script>")
    User getById(Long id);

    /** Payment-only lookup through an authorized order; returns no customer profile. */
    @Select("<script><bind name='_tenant' value='@com.junzhou.context.RequestScope@requireTenantId()'/>"
            + "<bind name='_store' value='@com.junzhou.context.RequestScope@requireStoreId()'/>"
            + "<bind name='_user' value='@com.junzhou.context.RequestScope@requireOrderUserId()'/>"
            + "select u.id,u.openid from `user` u join orders o on o.user_id=u.id "
            + "where o.id=#{orderId} and o.tenant_id=#{_tenant} and o.store_id=#{_store}"
            + " and exists (select 1 from store s where s.id=o.store_id and s.tenant_id=o.tenant_id)"
            + "<if test='_user != null'> and o.user_id=#{_user}</if></script>")
    User getPaymentUserByOrderId(Long orderId);

    /**
     * 认证引导注册：仅接受微信服务已验证的 openid
     * @param user
     */
    void insert(User user);
}
