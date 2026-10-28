package com.junzhou.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.junzhou.constant.MessageConstant;
import com.junzhou.constant.StatusConstant;
import com.junzhou.dto.SetmealDTO;
import com.junzhou.dto.SetmealPageQueryDTO;
import com.junzhou.entity.Dish;
import com.junzhou.entity.Setmeal;
import com.junzhou.entity.SetmealDish;
import com.junzhou.exception.BaseException;
import com.junzhou.exception.DeletionNotAllowedException;
import com.junzhou.exception.SetmealEnableFailedException;
import com.junzhou.mapper.DishMapper;
import com.junzhou.mapper.SetmealDishMapper;
import com.junzhou.mapper.SetmealMapper;
import com.junzhou.result.PageResult;
import com.junzhou.service.SetmealService;
import com.junzhou.vo.DishItemVO;
import com.junzhou.vo.SetmealVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Service
@Slf4j
public class SetmealServiceImpl implements SetmealService {
    @Autowired
    private SetmealMapper setmealMapper;
    @Autowired
    private SetmealDishMapper setmealDishMapper;
    @Autowired
    private DishMapper dishMapper;

    /**
     * 新增套餐和对应的菜品关系
     * @param setmealDTO
     */
    @Override
    @Transactional
    public void saveWithDish(SetmealDTO setmealDTO) {
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO, setmeal);
        // 未指定售卖状态时，新增套餐默认停售。
        if (setmeal.getStatus() == null) {
            setmeal.setStatus(StatusConstant.DISABLE);
        }
        validateStatus(setmeal.getStatus());
        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
        prepareDishes(setmealDishes, setmeal.getStatus());

        setmealMapper.insert(setmeal);
        // 回填套餐ID，建立套餐与菜品的关联关系。
        for (SetmealDish setmealDish : setmealDishes) {
            setmealDish.setSetmealId(setmeal.getId());
        }
        setmealDishMapper.insertBatch(setmealDishes);
    }

    /**
     * 套餐分页查询
     * @param setmealPageQueryDTO
     * @return
     */
    @Override
    public PageResult pageQuery(SetmealPageQueryDTO setmealPageQueryDTO) {
        PageHelper.startPage(setmealPageQueryDTO.getPage(), setmealPageQueryDTO.getPageSize());
        Page<SetmealVO> page = setmealMapper.pageQuery(setmealPageQueryDTO);
        return new PageResult(page.getTotal(), page.getResult());
    }

    /**
     * 批量删除套餐和对应的菜品关系
     * @param ids
     */
    @Override
    @Transactional
    public void deleteBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new DeletionNotAllowedException(MessageConstant.SETMEAL_IDS_EMPTY);
        }
        // 全部校验通过后再删除，起售中的套餐不能删除。
        for (Long id : ids) {
            Setmeal setmeal = setmealMapper.getById(id);
            if (setmeal == null) {
                throw new DeletionNotAllowedException(MessageConstant.SETMEAL_NOT_EXIST);
            }
            if (StatusConstant.ENABLE.equals(setmeal.getStatus())) {
                throw new DeletionNotAllowedException(MessageConstant.SETMEAL_ON_SALE);
            }
        }
        setmealDishMapper.deleteBySetmealIds(ids);
        setmealMapper.deleteBatch(ids);
    }

    /**
     * 根据id查询套餐和对应的菜品
     * @param id
     * @return
     */
    @Override
    public SetmealVO getByIdWithDish(Long id) {
        getSetmeal(id);
        SetmealVO setmealVO = setmealMapper.getVOById(id);
        setmealVO.setSetmealDishes(setmealDishMapper.getBySetmealId(id));
        return setmealVO;
    }

    /**
     * 修改套餐和对应的菜品关系
     * @param setmealDTO
     */
    @Override
    @Transactional
    public void updateWithDish(SetmealDTO setmealDTO) {
        if (setmealDTO == null || setmealDTO.getId() == null) {
            throw new BaseException(MessageConstant.SETMEAL_ID_REQUIRED);
        }
        Setmeal original = getSetmeal(setmealDTO.getId());
        Setmeal setmeal = new Setmeal();
        BeanUtils.copyProperties(setmealDTO, setmeal);
        Integer status = setmeal.getStatus() == null ? original.getStatus() : setmeal.getStatus();
        validateStatus(status);
        List<SetmealDish> setmealDishes = setmealDTO.getSetmealDishes();
        prepareDishes(setmealDishes, status);

        setmealMapper.updateById(setmeal);
        // 先删除旧关系，再保存新的菜品列表。
        setmealDishMapper.deleteBySetmealIds(Collections.singletonList(setmeal.getId()));
        for (SetmealDish setmealDish : setmealDishes) {
            setmealDish.setSetmealId(setmeal.getId());
        }
        setmealDishMapper.insertBatch(setmealDishes);
    }

    /**
     * 套餐起售、停售
     * @param status 1为起售，0为停售
     * @param id 套餐id
     */
    @Override
    @Transactional
    public void startOrStop(Integer status, Long id) {
        validateStatus(status);
        getSetmeal(id);
        if (StatusConstant.ENABLE.equals(status)) {
            List<SetmealDish> setmealDishes = setmealDishMapper.getBySetmealId(id);
            if (setmealDishes.isEmpty()) {
                throw new SetmealEnableFailedException(MessageConstant.SETMEAL_DISHES_EMPTY);
            }
            // 使用菜品当前状态校验，包含停售或已删除菜品时不能起售。
            for (SetmealDish setmealDish : setmealDishes) {
                Dish dish = dishMapper.getById(setmealDish.getDishId());
                if (dish == null || !StatusConstant.ENABLE.equals(dish.getStatus())) {
                    throw new SetmealEnableFailedException(MessageConstant.SETMEAL_ENABLE_FAILED);
                }
            }
        }
        Setmeal setmeal = Setmeal.builder().id(id).status(status).build();
        setmealMapper.updateById(setmeal);
    }

    /**
     * 条件查询套餐
     */
    @Override
    public List<Setmeal> list(Setmeal setmeal) {
        return setmealMapper.list(setmeal);
    }

    /**
     * 根据套餐id查询菜品选项
     */
    @Override
    public List<DishItemVO> getDishItemById(Long id) {
        return setmealMapper.getDishItemBySetmealId(id);
    }

    private Setmeal getSetmeal(Long id) {
        if (id == null) {
            throw new BaseException(MessageConstant.SETMEAL_ID_REQUIRED);
        }
        Setmeal setmeal = setmealMapper.getById(id);
        if (setmeal == null) {
            throw new BaseException(MessageConstant.SETMEAL_NOT_EXIST);
        }
        return setmeal;
    }

    private void validateStatus(Integer status) {
        if (!StatusConstant.ENABLE.equals(status) && !StatusConstant.DISABLE.equals(status)) {
            throw new BaseException(MessageConstant.SETMEAL_STATUS_INVALID);
        }
    }

    /**
     * 校验菜品关系，并从菜品表取得名称和原价。
     */
    private void prepareDishes(List<SetmealDish> setmealDishes, Integer status) {
        if (setmealDishes == null || setmealDishes.isEmpty()) {
            throw new BaseException(MessageConstant.SETMEAL_DISHES_EMPTY);
        }
        for (SetmealDish setmealDish : setmealDishes) {
            if (setmealDish == null || setmealDish.getDishId() == null
                    || setmealDish.getCopies() == null || setmealDish.getCopies() <= 0) {
                throw new BaseException(MessageConstant.SETMEAL_DISH_INVALID);
            }
            Dish dish = dishMapper.getById(setmealDish.getDishId());
            if (dish == null) {
                throw new BaseException(MessageConstant.DISH_NOT_EXIST);
            }
            if (StatusConstant.ENABLE.equals(status) && !StatusConstant.ENABLE.equals(dish.getStatus())) {
                throw new SetmealEnableFailedException(MessageConstant.SETMEAL_ENABLE_FAILED);
            }
            setmealDish.setName(dish.getName());
            setmealDish.setPrice(dish.getPrice());
        }
    }
}
