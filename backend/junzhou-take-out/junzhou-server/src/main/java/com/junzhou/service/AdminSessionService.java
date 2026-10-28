package com.junzhou.service;

import com.junzhou.entity.Employee;

/** 管理员会话业务接口。 */
public interface AdminSessionService {
    String SESSION_ATTRIBUTE = "sky.admin.sessionId";

    /**
     * 锁定员工记录并签发管理员登录令牌，避免与禁用账号或修改密码并发。
     * @param authenticated 已认证的员工
     * @return 登录令牌
     */
    String issue(Employee authenticated);

    /**
     * 校验管理员会话是否有效。
     * @param sessionId 会话标识
     * @param employeeId 员工ID
     * @return 会话是否有效
     */
    boolean isActive(String sessionId, Long employeeId);

    /**
     * 注销当前管理员会话。
     * @param sessionId 会话标识
     * @param employeeId 员工ID
     */
    void logout(String sessionId, Long employeeId);

}
