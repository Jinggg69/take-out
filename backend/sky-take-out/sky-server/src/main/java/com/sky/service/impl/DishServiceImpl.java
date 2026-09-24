package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.dto.DishDTO;
import com.sky.dto.DishPageQueryDTO;
import com.sky.entity.Dish;
import com.sky.entity.DishFlavor;
import com.sky.exception.BaseException;
import com.sky.exception.DeletionNotAllowedException;
import com.sky.mapper.DishFlavorMapper;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealDishMapper;
import com.sky.result.PageResult;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Service
@Slf4j
public class DishServiceImpl implements DishService {
    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private DishFlavorMapper dishFlavorMapper;
    @Autowired
    private SetmealDishMapper setmealDishMapper;

    /**
     * 新增菜品
     * @param dishDTO
     */
    @Override
    @Transactional
    public void saveWithFlavor(DishDTO dishDTO) {
        log.info("新增菜品：{}",dishDTO);
        Dish dish = new Dish();
        BeanUtils.copyProperties(dishDTO, dish);
        dishMapper.insert(dish);
        // 获取菜品口味列表
        List<DishFlavor> flavors = dishDTO.getFlavors();
        // 口味列表不为空时，批量保存口味数据
        if (flavors != null && !flavors.isEmpty()) {
            // 回填菜品ID，建立口味与菜品的关联关系
            for (DishFlavor flavor : flavors) {
                flavor.setDishId(dish.getId());
            }
            // 批量插入口味数据
            dishMapper.insertFlavors(flavors);
        }
    }
    /**
     * 菜品分页查询
     * @param dishPageQueryDTO
     * @return
     */
    @Override
    public PageResult pageQuery(DishPageQueryDTO dishPageQueryDTO) {
        PageHelper.startPage(dishPageQueryDTO.getPage(), dishPageQueryDTO.getPageSize());
        Page<DishVO> page = dishMapper.pageQuery(dishPageQueryDTO);
        return new PageResult(page.getTotal(), page.getResult());
    }

    /**
     * 根据ID删除菜品
     * @param ids
     * @return
     */
    @Override
    @Transactional
    public void deleteBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new DeletionNotAllowedException(MessageConstant.DISH_IDS_EMPTY);
        }

        //判断当前菜品是否能够删除---是否在售
        for (Long id : ids) {
            Dish dish = dishMapper.getById(id);
            if (dish == null) {
                throw new DeletionNotAllowedException(MessageConstant.DISH_NOT_FOUND);
            }
            if (StatusConstant.ENABLE.equals(dish.getStatus())) {
                throw new DeletionNotAllowedException(MessageConstant.DISH_ON_SALE);
            }
        }
        //判断当前菜品是否能够删除---是否关联了套餐
        List<Long> setmealIds = setmealDishMapper.getSetmealIdsByDishIds(ids);
        if (setmealIds != null && !setmealIds.isEmpty()) {
            throw new DeletionNotAllowedException(MessageConstant.DISH_BE_RELATED_BY_SETMEAL);
        }

        dishFlavorMapper.deleteByDishIds(ids);
        dishMapper.deleteBatch(ids);
    }

    /**
     * 根据id查询菜品和对应的口味
     * @param id
     * @return
     */
    @Override
    public DishVO getByIdWithFlavor(Long id) {
        // 根据id查询菜品
        Dish dish = dishMapper.getById(id);
        if (dish == null) {
            throw new BaseException(MessageConstant.DISH_NOT_EXIST);
        }
        // 根据菜品id查询对应的口味列表
        List<DishFlavor> dishFlavors = dishFlavorMapper.getByDishId(id);
        // 将菜品和口味列表封装到DishVO中
        DishVO dishVO = new DishVO();
        BeanUtils.copyProperties(dish, dishVO);
        dishVO.setFlavors(dishFlavors);
        return dishVO;
    }

    /**
     * 修改菜品和对应的口味
     * @param dishDTO
     */
    @Override
    @Transactional
    public void updateWithFlavor(DishDTO dishDTO) {
        if (dishDTO == null || dishDTO.getId() == null) {
            throw new BaseException(MessageConstant.DISH_ID_REQUIRED);
        }
        if (dishMapper.getById(dishDTO.getId()) == null) {
            throw new BaseException(MessageConstant.DISH_NOT_EXIST);
        }

        // 更新菜品信息
        Dish dish = new Dish();
        BeanUtils.copyProperties(dishDTO, dish);
        dishMapper.updateById(dish);

        // 删除原有的口味信息
        dishFlavorMapper.deleteByDishIds(Collections.singletonList(dish.getId()));

        // 插入新的口味信息
        List<DishFlavor> flavors = dishDTO.getFlavors();
        if (flavors != null && !flavors.isEmpty()) {
            for (DishFlavor flavor : flavors) {
                flavor.setDishId(dish.getId());
            }
            dishMapper.insertFlavors(flavors);
        }
    }

    @Override
    public void startOrStop(Integer status, Long id) {
        if (!StatusConstant.ENABLE.equals(status) && !StatusConstant.DISABLE.equals(status)) {
            throw new BaseException(MessageConstant.DISH_STATUS_INVALID);
        }
        if (id == null) {
            throw new BaseException(MessageConstant.DISH_ID_REQUIRED);
        }
        if (dishMapper.getById(id) == null) {
            throw new BaseException(MessageConstant.DISH_NOT_EXIST);
        }

        // 只更新售卖状态，修改时间和修改人由公共字段填充切面处理。
        Dish dish = Dish.builder().id(id).status(status).build();
        dishMapper.updateById(dish);
    }
}
