package com.sky.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DishFlavorMapper {

    /**
     * 批量删除菜品对应的口味
     * @param dishIds 菜品 ID 列表
     */
    void deleteByDishIds(@Param("dishIds") List<Long> dishIds);
}
