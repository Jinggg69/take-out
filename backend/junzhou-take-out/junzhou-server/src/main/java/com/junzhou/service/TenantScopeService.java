package com.junzhou.service;

import com.junzhou.context.RequestScope;

/** 租户与门店请求范围服务。 */
public interface TenantScopeService {
    RequestScope forEmployeeIdentity(Long employeeId);

    /**
     * 确认当前数据库仍处于允许的单店过渡状态。
     * @throws SecurityException 不满足单店条件时拒绝访问
     */
    void requireLegacySingleStore();

    /**
     * 从员工归属和允许的门店构造管理端请求范围。
     * @param employeeId 已认证的员工 ID
     * @param selectedStore 客户端选择的门店 ID，可为空
     * @return 已验证的请求范围
     * @throws SecurityException 员工或门店无效、无权访问时拒绝访问
     */
    RequestScope forEmployee(Long employeeId, Long selectedStore);

    /**
     * 从有效门店推导租户并构造用户端请求范围。
     * @param userId 已认证的用户 ID，匿名请求时为空
     * @param selectedStore 客户端选择的门店 ID
     * @return 已验证的请求范围
     * @throws SecurityException 门店不存在或不可用时拒绝访问
     */
    RequestScope forUser(Long userId, Long selectedStore);
}
