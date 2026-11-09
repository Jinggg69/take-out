package com.junzhou.service.impl;

import com.junzhou.service.OrderPricingService;

import com.junzhou.constant.MessageConstant;
import com.junzhou.entity.Dish;
import com.junzhou.entity.Setmeal;
import com.junzhou.entity.SetmealDish;
import com.junzhou.entity.ShoppingCart;
import com.junzhou.exception.OrderBusinessException;
import com.junzhou.exception.ShoppingCartBusinessException;
import com.junzhou.mapper.DishMapper;
import com.junzhou.mapper.SetmealDishMapper;
import com.junzhou.mapper.SetmealMapper;
import com.junzhou.mapper.ShoppingCartMapper;
import com.junzhou.vo.OrderQuoteVO;
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
public class OrderPricingServiceImpl implements OrderPricingService {
    @Autowired private com.junzhou.service.StoreSettingsService storeSettings;
    private static final BigDecimal MAX_ORDER_AMOUNT = new BigDecimal("99999999.99");
    @Autowired private ShoppingCartMapper shoppingCartMapper;
    @Autowired private DishMapper dishMapper;
    @Autowired private SetmealMapper setmealMapper;
    @Autowired private SetmealDishMapper setmealDishMapper;

    /** {@inheritDoc} */
    @Transactional(readOnly = true)
    @Override
    public OrderQuoteVO quote() {
        Long userId = com.junzhou.context.RequestScope.requireUserId();
        if (userId == null) throw new OrderBusinessException(MessageConstant.USER_NOT_LOGIN);
        List<ShoppingCart> cart = shoppingCartMapper.list(ShoppingCart.builder().userId(userId).build());
        if (cart == null || cart.isEmpty()) {
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }
        com.junzhou.entity.StoreSettings settings = storeSettings.requireOpen();
        BigDecimal goods = new BigDecimal("0.00");
        long count = 0;
        List<ShoppingCart> items = new ArrayList<>();
        for (ShoppingCart row : cart) {
            if (row == null || !userId.equals(row.getUserId()) || row.getNumber() == null || row.getNumber() <= 0
                    || (row.getDishId() == null) == (row.getSetmealId() == null)) {
                throw new OrderBusinessException("购物车商品或数量异常，请返回菜单检查");
            }
            ShoppingCart item = priceAvailableItem(row);
            goods = goods.add(item.getAmount().multiply(BigDecimal.valueOf(item.getNumber())));
            count += item.getNumber();
            items.add(item);
        }
        if (goods.compareTo(settings.getMinimumOrderAmount()) < 0) {
            throw new OrderBusinessException("商品金额未达到门店起送价");
        }
        if (count > Integer.MAX_VALUE) throw new OrderBusinessException("商品数量超出允许范围");
        BigDecimal packFee = settings.getPackFeePerItem().multiply(BigDecimal.valueOf(count));
        BigDecimal deliveryFee = settings.getDeliveryFee();
        if (settings.getFreeDeliveryThreshold() != null && goods.compareTo(settings.getFreeDeliveryThreshold()) >= 0)
            deliveryFee = new BigDecimal("0.00");
        BigDecimal total = goods.add(packFee).add(deliveryFee);
        if (total.compareTo(MAX_ORDER_AMOUNT) > 0) {
            throw new OrderBusinessException("订单金额超出允许范围，请减少商品数量");
        }
        return new OrderQuoteVO(items, goods, (int) count, deliveryFee, total, packFee, settings.getId(), settings.getVersion());
    }

    /** {@inheritDoc} */
    @Override
    public ShoppingCart refreshItem(ShoppingCart row) {
        requireOpenStore();
        return priceAvailableItem(row);
    }

    private void requireOpenStore() {
        storeSettings.requireOpen();
    }

    private ShoppingCart priceAvailableItem(ShoppingCart row) {
        ShoppingCart item = new ShoppingCart();
        BeanUtils.copyProperties(row, item);
        if (row.getDishId() != null) {
            Dish dish = requireAvailableDish(row.getDishId());
            item.setAmount(requirePrice(dish.getPrice()));
            item.setName(dish.getName());
            item.setImage(dish.getImage());
        } else {
            Setmeal setmeal = setmealMapper.getAvailableForStore(row.getSetmealId());
            if (setmeal == null) {
                throw new OrderBusinessException("购物车中的套餐已停售或删除，请返回菜单检查");
            }
            if (setmealDishMapper.countInvalidDishes(setmeal.getId()) != 0) {
                throw new OrderBusinessException("套餐组成归属异常，请返回菜单检查");
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
        return item;
    }

    /** 校验菜品是否可售。 */
    private Dish requireAvailableDish(Long id) {
        Dish dish = dishMapper.getAvailableForStore(id);
        if (dish == null) {
            throw new OrderBusinessException("购物车或套餐中的菜品已停售或删除，请返回菜单检查");
        }
        return dish;
    }

    /** 校验商品价格。 */
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
