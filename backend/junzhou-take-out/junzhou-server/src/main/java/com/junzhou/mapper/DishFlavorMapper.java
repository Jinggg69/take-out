package com.junzhou.mapper;

import com.junzhou.entity.DishFlavor;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DishFlavorMapper {

    /**
     * 批量删除菜品对应的口味
     * @param dishIds 菜品 ID 列表
     */
    void deleteByDishIds(@Param("dishIds") List<Long> dishIds);

    /**
     * 根据菜品 ID 查询对应的口味列表
     * @param dishId 菜品 ID
     * @return 菜品口味列表
     */
    @Select("<script><bind name=\"scopeTenant\" value=\"@com.junzhou.context.RequestScope@requireTenantId()\"/>select f.* from dish_flavor f join dish d on d.id = f.dish_id where f.dish_id = #{dishId} and d.tenant_id = #{scopeTenant}</script>")
    List<DishFlavor> getByDishId(Long dishId);
}
