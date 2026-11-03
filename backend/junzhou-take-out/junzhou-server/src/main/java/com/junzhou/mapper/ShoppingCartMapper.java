package com.junzhou.mapper;

import com.junzhou.dto.ShoppingCartDTO;
import com.junzhou.entity.ShoppingCart;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ShoppingCartMapper {
    /**
     * 动态查询购物车列表
     * @param shoppingCart
     * @return
     */
    List<ShoppingCart> list(ShoppingCart shoppingCart);

    /**
     * 更新购物车
     * @param shoppingCart
     */
    @Update("<script><bind name=\"scopeTenant\" value=\"@com.junzhou.context.RequestScope@requireTenantId()\"/><bind name=\"scopeStore\" value=\"@com.junzhou.context.RequestScope@requireStoreId()\"/><bind name=\"scopeUser\" value=\"@com.junzhou.context.RequestScope@requireUserId()\"/>update shopping_cart set number=number+#{quantity} where id=#{id} and user_id=#{userId} and number>0 and number&lt;=2147483647-#{quantity} and tenant_id=#{scopeTenant} and store_id=#{scopeStore} and user_id=#{scopeUser}</script>")
    int increment(@Param("id") Long id, @Param("userId") Long userId, @Param("quantity") int quantity);

    @Update("<script><bind name=\"scopeTenant\" value=\"@com.junzhou.context.RequestScope@requireTenantId()\"/><bind name=\"scopeStore\" value=\"@com.junzhou.context.RequestScope@requireStoreId()\"/><bind name=\"scopeUser\" value=\"@com.junzhou.context.RequestScope@requireUserId()\"/>update shopping_cart set number=number-1 where id=#{id} and user_id=#{userId} and number>1 and tenant_id=#{scopeTenant} and store_id=#{scopeStore} and user_id=#{scopeUser}</script>")
    int decrement(@Param("id") Long id, @Param("userId") Long userId);

    @Delete("<script><bind name=\"scopeTenant\" value=\"@com.junzhou.context.RequestScope@requireTenantId()\"/><bind name=\"scopeStore\" value=\"@com.junzhou.context.RequestScope@requireStoreId()\"/><bind name=\"scopeUser\" value=\"@com.junzhou.context.RequestScope@requireUserId()\"/>delete from shopping_cart where id=#{id} and user_id=#{userId} and number=1 and tenant_id=#{scopeTenant} and store_id=#{scopeStore} and user_id=#{scopeUser}</script>")
    int deleteSingle(@Param("id") Long id, @Param("userId") Long userId);

    /** Used only under the user lock when consolidating a legacy item. */
    @Update("<script><bind name=\"scopeTenant\" value=\"@com.junzhou.context.RequestScope@requireTenantId()\"/><bind name=\"scopeStore\" value=\"@com.junzhou.context.RequestScope@requireStoreId()\"/><bind name=\"scopeUser\" value=\"@com.junzhou.context.RequestScope@requireUserId()\"/>update shopping_cart set number=#{number},dish_flavor=#{dishFlavor} where id=#{id} and user_id=#{userId} and tenant_id=#{scopeTenant} and store_id=#{scopeStore} and user_id=#{scopeUser}</script>")
    int consolidate(ShoppingCart cart);

    @Update("<script><bind name=\"scopeTenant\" value=\"@com.junzhou.context.RequestScope@requireTenantId()\"/><bind name=\"scopeStore\" value=\"@com.junzhou.context.RequestScope@requireStoreId()\"/><bind name=\"scopeUser\" value=\"@com.junzhou.context.RequestScope@requireUserId()\"/>update shopping_cart set name=#{name},image=#{image},amount=#{amount},dish_flavor=#{dishFlavor} where id=#{id} and user_id=#{userId} and tenant_id=#{scopeTenant} and store_id=#{scopeStore} and user_id=#{scopeUser}</script>")
    int refreshItem(ShoppingCart cart);

    /**
     * 插入购物车
     * @param shoppingCart
     */
    @Insert("<script><bind name=\"scopeTenant\" value=\"@com.junzhou.context.RequestScope@requireTenantId()\"/><bind name=\"scopeStore\" value=\"@com.junzhou.context.RequestScope@requireStoreId()\"/><bind name=\"scopeUser\" value=\"@com.junzhou.context.RequestScope@requireUserId()\"/>insert into shopping_cart (tenant_id,store_id,user_id, dish_id, setmeal_id, name, image, dish_flavor, number, amount, create_time) select #{scopeTenant},#{scopeStore},#{scopeUser}, #{dishId}, #{setmealId}, #{name}, #{image}, #{dishFlavor}, #{number}, #{amount}, #{createTime} where #{userId}=#{scopeUser} and ((#{dishId} is not null and #{setmealId} is null and exists(select 1 from dish where id=#{dishId} and tenant_id=#{scopeTenant})) or (#{dishId} is null and #{setmealId} is not null and exists(select 1 from setmeal where id=#{setmealId} and tenant_id=#{scopeTenant})))</script>")
    int insert(ShoppingCart shoppingCart);

    /**
     * 清空购物车
     * @param userId
     */
    @Delete("<script><bind name=\"scopeTenant\" value=\"@com.junzhou.context.RequestScope@requireTenantId()\"/><bind name=\"scopeStore\" value=\"@com.junzhou.context.RequestScope@requireStoreId()\"/><bind name=\"scopeUser\" value=\"@com.junzhou.context.RequestScope@requireUserId()\"/>delete from shopping_cart where user_id = #{userId} and tenant_id=#{scopeTenant} and store_id=#{scopeStore} and user_id=#{scopeUser}</script>")
    void delete(Long userId);

    /**
     * 根据id和userId删除购物车
     * @param id
     * @param userId
     */
    @Delete("<script><bind name=\"scopeTenant\" value=\"@com.junzhou.context.RequestScope@requireTenantId()\"/><bind name=\"scopeStore\" value=\"@com.junzhou.context.RequestScope@requireStoreId()\"/><bind name=\"scopeUser\" value=\"@com.junzhou.context.RequestScope@requireUserId()\"/>delete from shopping_cart where id = #{id} and user_id = #{userId} and tenant_id=#{scopeTenant} and store_id=#{scopeStore} and user_id=#{scopeUser}</script>")
    void deleteByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
}
