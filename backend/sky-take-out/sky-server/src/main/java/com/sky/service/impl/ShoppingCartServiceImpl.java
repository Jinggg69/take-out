package com.sky.service.impl;

import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.ShoppingCart;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.service.CartAccessService;
import com.sky.service.OrderPricingService;
import com.sky.service.ShoppingCartService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ShoppingCartServiceImpl implements ShoppingCartService {
    @Autowired private ShoppingCartMapper shoppingCartMapper;
    @Autowired private CartAccessService cartAccessService;
    @Autowired private OrderPricingService orderPricingService;

    @Override
    @Transactional
    public void add(ShoppingCartDTO input) { add(input, 1); }

    @Override
    @Transactional
    public void add(ShoppingCartDTO input, int quantity) {
        ShoppingCart key = itemKey(input);
        if (quantity <= 0) throw new ShoppingCartBusinessException("商品数量必须大于零");
        key.setUserId(cartAccessService.lockCurrentCart());
        key.setNumber(quantity);
        ShoppingCart fresh = orderPricingService.refreshItem(key);
        ShoppingCart existing = consolidate(key);
        if (existing == null) {
            fresh.setCreateTime(LocalDateTime.now());
            shoppingCartMapper.insert(fresh);
        } else {
            if (shoppingCartMapper.increment(existing.getId(), key.getUserId(), quantity) != 1) {
                throw new ShoppingCartBusinessException("购物车数量超出范围，请减少商品");
            }
            fresh.setId(existing.getId());
            if (shoppingCartMapper.refreshItem(fresh) != 1) throw new ShoppingCartBusinessException("购物车已变化，请重试");
        }
    }

    @Override
    public List<ShoppingCart> showShoppingCart() {
        return shoppingCartMapper.list(ShoppingCart.builder().userId(cartAccessService.currentUserId()).build());
    }

    @Override
    @Transactional
    public void clean() { shoppingCartMapper.delete(cartAccessService.lockCurrentCart()); }

    @Override
    @Transactional
    public void sub(ShoppingCartDTO input) {
        ShoppingCart key = itemKey(input);
        key.setUserId(cartAccessService.lockCurrentCart());
        ShoppingCart existing = consolidate(key);
        if (existing == null) return;
        if (shoppingCartMapper.decrement(existing.getId(), key.getUserId()) == 0) {
            shoppingCartMapper.deleteSingle(existing.getId(), key.getUserId());
        }
    }

    private ShoppingCart itemKey(ShoppingCartDTO input) {
        if (input == null || (input.getDishId() == null) == (input.getSetmealId() == null)
                || (input.getDishId() != null && input.getDishId() <= 0)
                || (input.getSetmealId() != null && input.getSetmealId() <= 0)) {
            throw new ShoppingCartBusinessException("请指定一个有效的菜品或套餐");
        }
        String flavor = input.getDishId() == null ? "" : normalize(input.getDishFlavor());
        if (flavor.length() > 50) throw new ShoppingCartBusinessException("口味信息不能超过50个字符");
        return ShoppingCart.builder().dishId(input.getDishId()).setmealId(input.getSetmealId()).dishFlavor(flavor).build();
    }

    /** Compare canonical flavors in Java, including old null and surrounding whitespace. */
    private ShoppingCart consolidate(ShoppingCart key) {
        List<ShoppingCart> matches = new ArrayList<>();
        long count = 0;
        for (ShoppingCart row : shoppingCartMapper.list(key)) {
            String flavor = key.getSetmealId() == null ? normalize(row.getDishFlavor()) : "";
            if (!key.getDishFlavor().equals(flavor)) continue;
            if (row.getNumber() == null || row.getNumber() <= 0) throw new ShoppingCartBusinessException("购物车数量异常，请清空后重试");
            count += row.getNumber();
            if (count > Integer.MAX_VALUE) throw new ShoppingCartBusinessException("购物车数量超出范围，请减少商品");
            matches.add(row);
        }
        if (matches.isEmpty()) return null;
        ShoppingCart kept = matches.get(0);
        if (matches.size() > 1 || !key.getDishFlavor().equals(kept.getDishFlavor())) {
            kept.setNumber((int) count);
            kept.setDishFlavor(key.getDishFlavor());
            if (shoppingCartMapper.consolidate(kept) != 1) throw new ShoppingCartBusinessException("购物车已变化，请重试");
        }
        for (int i=1;i<matches.size();i++) shoppingCartMapper.deleteByIdAndUserId(matches.get(i).getId(), key.getUserId());
        return kept;
    }

    private String normalize(String flavor) { return flavor == null ? "" : flavor.trim(); }
}
