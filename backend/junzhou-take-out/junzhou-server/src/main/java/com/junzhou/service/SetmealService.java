package com.junzhou.service;

import com.junzhou.dto.SetmealDTO;
import com.junzhou.dto.SetmealPageQueryDTO;
import com.junzhou.entity.Setmeal;
import com.junzhou.result.PageResult;
import com.junzhou.vo.DishItemVO;
import com.junzhou.vo.SetmealVO;

import java.util.List;

public interface SetmealService {
    /**
     * 新增套餐和对应的菜品关系
     * @param setmealDTO
     */
    void saveWithDish(SetmealDTO setmealDTO);

    /**
     * 套餐分页查询
     * @param setmealPageQueryDTO
     * @return
     */
    PageResult pageQuery(SetmealPageQueryDTO setmealPageQueryDTO);

    /**
     * 批量删除套餐
     * @param ids
     */
    void deleteBatch(List<Long> ids);

    /**
     * 根据id查询套餐和对应的菜品
     * @param id
     * @return
     */
    SetmealVO getByIdWithDish(Long id);

    /**
     * 修改套餐和对应的菜品关系
     * @param setmealDTO
     */
    void updateWithDish(SetmealDTO setmealDTO);

    /**
     * 套餐起售、停售
     * @param status 1为起售，0为停售
     * @param id 套餐id
     */
    void startOrStop(Integer status, Long id);

    /**
     * 条件查询套餐
     * @param setmeal
     * @return
     */
    List<Setmeal> list(Setmeal setmeal);

    /**
     * 根据套餐id查询菜品选项
     * @param id
     * @return
     */
    List<DishItemVO> getDishItemById(Long id);
}
