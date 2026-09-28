package com.sky.mapper;

import com.sky.entity.SetmealDish;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SetmealDishMapper {
    /**
     * 根据菜品ID列表查询对应的套餐ID列表
      * @param dishIds
     * @return
     */
    List<Long> getSetmealIdsByDishIds(@Param("dishIds") List<Long> dishIds);

    /**
     * 批量保存套餐菜品关系
     */
    void insertBatch(@Param("setmealDishes") List<SetmealDish> setmealDishes);

    /**
     * 根据套餐id查询菜品关系
     */
    List<SetmealDish> getBySetmealId(Long setmealId);

    /**
     * 根据套餐ID列表删除菜品关系
     */
    void deleteBySetmealIds(@Param("setmealIds") List<Long> setmealIds);
}
