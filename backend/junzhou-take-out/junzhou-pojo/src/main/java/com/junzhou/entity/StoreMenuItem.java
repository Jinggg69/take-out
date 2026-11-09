package com.junzhou.entity;

import lombok.Data;
import java.math.BigDecimal;

/** Tenant catalog item and its current store's optional sale configuration. */
@Data
public class StoreMenuItem {
    private Long id;
    private String name;
    private BigDecimal price;
    private Integer onSale;
    private Integer soldOut;
    private BigDecimal overridePrice;
    private Integer sort;
}
