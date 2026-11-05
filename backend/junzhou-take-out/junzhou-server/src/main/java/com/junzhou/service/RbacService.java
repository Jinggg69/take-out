package com.junzhou.service;

import com.junzhou.security.PermissionAction;
import com.junzhou.security.RoleGrantInput;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** 角色权限、门店授权及管理员保护业务接口。 */
public interface RbacService {
    /** 判断员工是否拥有指定门店的动作权限。 */
    boolean permits(Long tenant, Long employee, Long store, PermissionAction action);

    /** 校验当前请求的动作权限及数据范围，无权访问时拒绝。 */
    void require(PermissionAction action);

    /** 查询员工可以访问的有效门店。 */
    Set<Long> accessibleStores(Long tenant, Long employee);

    /** 锁定当前租户；调用方必须在整个变更事务中持有此锁。 */
    void lock();

    /** 校验至少保留一名有效商户管理员；须在变更事务内调用。 */
    void ensureAdministrator();

    /** 查询当前员工的门店与动作权限上下文。 */
    Map<String, Object> context();

    /** 查询当前租户的角色权限及员工任职配置。 */
    Map<String, Object> configuration();

    /** 整体替换角色权限，保护最后管理员并撤销当前租户会话。 */
    void replacePermissions(String role, List<String> actions);

    /** 整体替换员工角色及门店任职，保护最后管理员并撤销会话。 */
    void replaceAssignments(Long employee, List<RoleGrantInput> grants);
}
