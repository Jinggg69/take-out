package com.junzhou.service;

import com.junzhou.entity.ShoppingCart;
import com.junzhou.vo.OrderQuoteVO;

/** 订单计价业务接口。 */
public interface OrderPricingService {
    /**
     * 计算当前购物车的订单报价。
     * @return 订单报价
     */
    OrderQuoteVO quote();

    /**
     * 刷新购物车商品的信息和价格。
     * @param row 购物车商品
     * @return 刷新后的商品
     */
    ShoppingCart refreshItem(ShoppingCart row);

}
