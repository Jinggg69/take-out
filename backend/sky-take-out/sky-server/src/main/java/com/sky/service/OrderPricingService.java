package com.sky.service;

import com.sky.constant.MessageConstant;
import com.sky.constant.StatusConstant;
import com.sky.context.BaseContext;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.entity.SetmealDish;
import com.sky.entity.ShoppingCart;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealDishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.vo.OrderQuoteVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** Shared checkout rules; cached cart prices and client fees are never pricing inputs. */
@Service
public class OrderPricingService {
    private static final BigDecimal DELIVERY_FEE = new BigDecimal("6.00");
    private static final BigDecimal MAX_ORDER_AMOUNT = new BigDecimal("99999999.99");
    @Autowired private ShoppingCartMapper shoppingCartMapper;
    @Autowired private DishMapper dishMapper;
    @Autowired private SetmealMapper setmealMapper;
    @Autowired private SetmealDishMapper setmealDishMapper;

    @Transactional(readOnly = true)
    public OrderQuoteVO quote() {
        Long userId = BaseContext.getCurrentId();
        if (userId == null) throw new OrderBusinessException(MessageConstant.USER_NOT_LOGIN);
        List<ShoppingCart> cart = shoppingCartMapper.list(ShoppingCart.builder().userId(userId).build());
        if (cart == null || cart.isEmpty()) {
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }
        BigDecimal goods = new BigDecimal("0.00");
        long count = 0;
        List<ShoppingCart> items = new ArrayList<>();
        for (ShoppingCart row : cart) {
            if (row == null || !userId.equals(row.getUserId()) || row.getNumber() == null || row.getNumber() <= 0
                    || (row.getDishId() == null) == (row.getSetmealId() == null)) {
                throw new OrderBusinessException("购物车商品或数量异常，请返回菜单检查");
            }
            ShoppingCart item = new ShoppingCart();
            BeanUtils.copyProperties(row, item);
            if (row.getDishId() != null) {
                Dish dish = requireAvailableDish(row.getDishId());
                item.setAmount(requirePrice(dish.getPrice()));
                item.setName(dish.getName());
                item.setImage(dish.getImage());
            } else {
                Setmeal setmeal = setmealMapper.getById(row.getSetmealId());
                if (setmeal == null || !StatusConstant.ENABLE.equals(setmeal.getStatus())) {
                    throw new OrderBusinessException("购物车中的套餐已停售或删除，请返回菜单检查");
                }
                List<SetmealDish> components = setmealDishMapper.getBySetmealId(setmeal.getId());
                if (components == null || components.isEmpty()) {
                    throw new OrderBusinessException("套餐组成异常，请返回菜单检查");
                }
                for (SetmealDish component : components) {
                    if (component == null || component.getDishId() == null
                            || component.getCopies() == null || component.getCopies() <= 0) {
                        throw new OrderBusinessException("套餐组成异常，请返回菜单检查");
                    }
                    requireAvailableDish(component.getDishId());
                }
                item.setAmount(requirePrice(setmeal.getPrice()));
                item.setName(setmeal.getName());
                item.setImage(setmeal.getImage());
            }
            goods = goods.add(item.getAmount().multiply(BigDecimal.valueOf(item.getNumber())));
            count += item.getNumber();
            items.add(item);
        }
        // Existing mini-program policy: one yuan per purchased unit, including one setmeal unit.
        BigDecimal total = goods.add(BigDecimal.valueOf(count)).add(DELIVERY_FEE);
        if (total.compareTo(MAX_ORDER_AMOUNT) > 0) {
            throw new OrderBusinessException("订单金额超出允许范围，请减少商品数量");
        }
        return new OrderQuoteVO(items, goods, (int) count, DELIVERY_FEE, total);
    }

    private Dish requireAvailableDish(Long id) {
        Dish dish = dishMapper.getById(id);
        if (dish == null || !StatusConstant.ENABLE.equals(dish.getStatus())) {
            throw new OrderBusinessException("购物车或套餐中的菜品已停售或删除，请返回菜单检查");
        }
        return dish;
    }

    private BigDecimal requirePrice(BigDecimal price) {
        if (price == null || price.signum() < 0) {
            throw new OrderBusinessException("商品价格异常，请联系商家");
        }
        try {
            return price.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new OrderBusinessException("商品价格精度异常，请联系商家");
        }
    }
}
