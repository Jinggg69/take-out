package com.junzhou.websocket;

import com.junzhou.context.RequestScope;
import com.junzhou.constant.JwtClaimsConstant;
import com.junzhou.properties.JwtProperties;
import com.junzhou.service.AdminSessionService;
import com.junzhou.service.TenantScopeService;
import com.junzhou.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import org.springframework.stereotype.Component;

@Component
public class OrderSocketAuth {
    private final JwtProperties jwt;
    private final AdminSessionService sessions;
    private final TenantScopeService scopes;
    public OrderSocketAuth(JwtProperties jwt, AdminSessionService sessions, TenantScopeService scopes) {
        this.jwt = jwt; this.sessions = sessions; this.scopes = scopes;
    }
    @org.springframework.beans.factory.annotation.Autowired private com.junzhou.service.RbacService rbac;
    public RequestScope authenticate(String token, Long store) {
        Claims claims = JwtUtil.parseJWT(jwt.getAdminSecretKey(), token);
        Long employee = Long.valueOf(String.valueOf(claims.get(JwtClaimsConstant.EMP_ID)));
        if (claims.getId() == null || !sessions.isActive(claims.getId(), employee))
            throw new SecurityException("Inactive employee session");
        RequestScope scope=scopes.forEmployee(employee,store);
        if(!rbac.permits(scope.getTenantId(),employee,scope.getStoreId(),com.junzhou.security.PermissionAction.ORDER_READ))throw new SecurityException("Order subscription denied");
        return scope;
    }
}
