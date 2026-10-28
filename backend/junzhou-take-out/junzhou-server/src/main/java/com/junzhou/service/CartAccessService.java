package com.junzhou.service;



/** 购物车访问业务接口。 */
public interface CartAccessService {
    /**
     * 获取并校验当前用户。
     * @return 当前用户ID
     */
    Long currentUserId();

    /**
     * 锁定当前用户的购物车。
     * @return 当前用户ID
     */
    Long lockCurrentCart();

}
