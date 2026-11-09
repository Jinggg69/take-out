package com.junzhou.service;

import com.junzhou.context.RequestScope;
import com.junzhou.entity.*;
import com.junzhou.exception.OrderBusinessException;
import com.junzhou.mapper.*;
import com.junzhou.vo.DishVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class StoreMenuService {
    @Autowired private StoreMenuMapper mapper;
    @Autowired private DishMapper dishMapper;
    @Autowired private DishFlavorMapper flavorMapper;
    @Autowired private SetmealMapper setmealMapper;
    @Autowired private SetmealDishMapper setmealDishMapper;

    public List<StoreMenuItem> list(String type) {
        RequestScope.requireStoreId();
        return dishType(type) ? mapper.dishes() : mapper.setmeals();
    }

    @Transactional
    public void save(String type, StoreMenuItem item) {
        RequestScope.requireStoreId();
        boolean dish = dishType(type);
        if (item == null || item.getId() == null || item.getId() <= 0 || !flag(item.getOnSale())
                || !flag(item.getSoldOut()) || item.getSort() == null)
            throw new OrderBusinessException("请填写有效的商品、售卖状态及排序");
        BigDecimal price = item.getOverridePrice();
        if (price != null && (price.signum() < 0 || price.scale() > 2 || price.compareTo(new BigDecimal("99999999.99")) > 0))
            throw new OrderBusinessException("门店价格须为非负金额，最多两位小数");
        if (mapper.lockStore() == null) throw new OrderBusinessException("门店已停用，请重新选店");
        if (dish ? dishMapper.getById(item.getId()) == null : setmealMapper.getById(item.getId()) == null)
            throw new OrderBusinessException("商品不存在或不属于当前租户");
        // SQL repeats catalog ownership in the insert so an unowned id can never be written.
        if (dish) mapper.saveDish(item); else mapper.saveSetmeal(item);
    }

    public List<DishVO> publicDishes(Long categoryId) {
        RequestScope.requireStoreId();
        List<DishVO> result = new ArrayList<>();
        for (Dish dish : mapper.publicDishes(categoryId)) {
            DishVO vo = new DishVO();
            BeanUtils.copyProperties(dish, vo);
            vo.setFlavors(flavorMapper.getByDishId(dish.getId()));
            result.add(vo);
        }
        return result;
    }

    public List<Setmeal> publicSetmeals(Long categoryId) {
        RequestScope.requireStoreId();
        List<Setmeal> result = new ArrayList<>();
        for (Setmeal setmeal : mapper.publicSetmeals(categoryId)) {
            if (setmealDishMapper.countInvalidDishes(setmeal.getId()) != 0) continue;
            List<SetmealDish> components = setmealDishMapper.getBySetmealId(setmeal.getId());
            if (components == null || components.isEmpty()) continue;
            boolean available = true;
            for (SetmealDish component : components) {
                if (component == null || component.getDishId() == null || component.getCopies() == null
                        || component.getCopies() <= 0 || dishMapper.getAvailableForStore(component.getDishId()) == null) {
                    available = false;
                    break;
                }
            }
            if (available) result.add(setmeal);
        }
        return result;
    }

    private boolean flag(Integer value) { return Integer.valueOf(0).equals(value) || Integer.valueOf(1).equals(value); }

    private boolean dishType(String type) {
        if ("dish".equals(type)) return true;
        if ("setmeal".equals(type)) return false;
        throw new OrderBusinessException("商品类型须为 dish 或 setmeal");
    }
}
