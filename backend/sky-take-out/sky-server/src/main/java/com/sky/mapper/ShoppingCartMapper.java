package com.sky.mapper;

import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.ShoppingCart;
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
    @Update("update shopping_cart set number=number+#{quantity} where id=#{id} and user_id=#{userId} "
            + "and number>0 and number<=2147483647-#{quantity}")
    int increment(@Param("id") Long id, @Param("userId") Long userId, @Param("quantity") int quantity);

    @Update("update shopping_cart set number=number-1 where id=#{id} and user_id=#{userId} and number>1")
    int decrement(@Param("id") Long id, @Param("userId") Long userId);

    @Delete("delete from shopping_cart where id=#{id} and user_id=#{userId} and number=1")
    int deleteSingle(@Param("id") Long id, @Param("userId") Long userId);

    /** Used only under the user lock when consolidating a legacy item. */
    @Update("update shopping_cart set number=#{number},dish_flavor=#{dishFlavor} where id=#{id} and user_id=#{userId}")
    int consolidate(ShoppingCart cart);

    @Update("update shopping_cart set name=#{name},image=#{image},amount=#{amount},dish_flavor=#{dishFlavor} "
            + "where id=#{id} and user_id=#{userId}")
    int refreshItem(ShoppingCart cart);

    /**
     * 插入购物车
     * @param shoppingCart
     */
    @Insert("insert into shopping_cart " +
        "(user_id, dish_id, setmeal_id, name, image, dish_flavor, number, amount, create_time) " +
        "values (#{userId}, #{dishId}, #{setmealId}, #{name}, #{image}, " +
        "#{dishFlavor}, #{number}, #{amount}, #{createTime})")
    void insert(ShoppingCart shoppingCart);

    /**
     * 清空购物车
     * @param userId
     */
    @Delete("delete from shopping_cart where user_id = #{userId}")
    void delete(Long userId);

    /**
     * 根据id和userId删除购物车
     * @param id
     * @param userId
     */
    @Delete("delete from shopping_cart where id = #{id} and user_id = #{userId}")
    void deleteByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
}
