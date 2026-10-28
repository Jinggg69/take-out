package com.junzhou.service;

import com.junzhou.dto.DishDTO;
import com.junzhou.dto.DishPageQueryDTO;
import com.junzhou.entity.Dish;
import com.junzhou.result.PageResult;
import com.junzhou.vo.DishVO;

import java.util.List;


public interface DishService {
    /**
     * 新增菜品和对应口味
     * @param dishDTO
     */
    public void saveWithFlavor(DishDTO dishDTO);

    /**
     * 菜品分页查询
     * @param dishPageQueryDTO
     * @return
     */
    PageResult pageQuery(DishPageQueryDTO dishPageQueryDTO);

    /**
     * 批量删除菜品
     * @param ids
     */
    void deleteBatch(List<Long> ids);

    /**
     * 根据id查询菜品和对应的口味
     * @param id
     * @return
     */
    DishVO getByIdWithFlavor(Long id);

    void updateWithFlavor(DishDTO dishDTO);

    /**
     * 根据分类id查询在售菜品
     * @param categoryId
     * @return
     */
    List<Dish> list(Long categoryId);

    /**
     * 条件查询菜品和口味
     * @param dish
     * @return
     */
    List<DishVO> listWithFlavor(Dish dish);

    /**
     * 菜品起售、停售
     * @param status 1为起售，0为停售
     * @param id 菜品id
     */
    void startOrStop(Integer status, Long id);
}
