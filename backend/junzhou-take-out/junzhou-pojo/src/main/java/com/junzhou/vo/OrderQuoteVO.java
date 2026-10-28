package com.junzhou.vo;

import com.junzhou.entity.ShoppingCart;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/** Current checkout quotation. All monetary values are yuan; items.amount is a unit price. */
@Data
@AllArgsConstructor
public class OrderQuoteVO implements Serializable {
    private List<ShoppingCart> items;
    private BigDecimal goodsAmount;
    private Integer packAmount;
    private BigDecimal deliveryFee;
    private BigDecimal amount;
}
