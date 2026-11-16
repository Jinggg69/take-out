package com.junzhou.interceptor;

import com.junzhou.context.BaseContext;
import com.junzhou.context.RequestScope;
import com.junzhou.service.TenantScopeService;
import com.junzhou.annotation.MiniappEntry;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.AsyncHandlerInterceptor;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Runs after authentication; public menu requests carry no user identity. */
@Component
public class RequestScopeInterceptor implements AsyncHandlerInterceptor {
    private final TenantScopeService scopes;
    public RequestScopeInterceptor(TenantScopeService scopes) { this.scopes = scopes; }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        RequestScope.clear();
        com.junzhou.security.RbacRequestContext.begin(request);
        try {
            if (!request.getRequestURI().startsWith(request.getContextPath() + "/admin/") && handler instanceof HandlerMethod) {
                HandlerMethod method=(HandlerMethod)handler;
                MiniappEntry entry=AnnotationUtils.findAnnotation(method.getMethod(),MiniappEntry.class);
                if(entry==null) entry=AnnotationUtils.findAnnotation(method.getBeanType(),MiniappEntry.class);
                if(entry!=null) {
                    Long user=(Long)request.getAttribute(JwtTokenUserInterceptor.USER_ATTRIBUTE);
                    if(entry.value()==MiniappEntry.Scope.PUBLIC) {
                        BaseContext.removeCurrentId();
                    } else {
                        if(user==null || user<=0) {
                            BaseContext.removeCurrentId();
                            com.junzhou.security.RbacRequestContext.clear(request);
                            MiniappResponses.error(response,401,"请先登录"); return false;
                        }
                        RequestScope.set(new RequestScope(null,user,null,null,java.util.Collections.emptySet()));
                    }
                    return true;
                }
            }
            String value = request.getHeader("X-Store-Id");
            Long store = value == null || value.isEmpty() ? null : Long.valueOf(value);
            RequestScope scope;
            if (request.getRequestURI().startsWith(request.getContextPath() + "/admin/")) {
                Long employee = BaseContext.getCurrentId();
                if (employee == null) throw new SecurityException("Missing employee");
                boolean selfService=false;
                if(handler instanceof org.springframework.web.method.HandlerMethod){
                    org.springframework.web.method.HandlerMethod method=(org.springframework.web.method.HandlerMethod)handler;
                    selfService=method.getBeanType()==com.junzhou.controller.admin.EmployeeController.class
                        && (method.getMethod().getName().equals("logout") || method.getMethod().getName().equals("editPassword"));
                }
                selfService = selfService || (handler instanceof org.springframework.web.method.HandlerMethod
                    && ((org.springframework.web.method.HandlerMethod)handler).getBeanType()==com.junzhou.controller.admin.RbacController.class
                    && ((org.springframework.web.method.HandlerMethod)handler).getMethod().getName().equals("context"));
                scope = selfService?scopes.forEmployeeIdentity(employee):scopes.forEmployee(employee, store);
            } else {
                Long user = (Long) request.getAttribute(JwtTokenUserInterceptor.USER_ATTRIBUTE);
                if (user == null) BaseContext.removeCurrentId();
                boolean discovery = request.getRequestURI().equals(request.getContextPath() + "/user/stores");
                scope = discovery ? scopes.forStoreDiscovery(user, store) : scopes.forUser(user, store);
            }
            RequestScope.set(scope);
            return true;
        } catch (SecurityException | IllegalArgumentException ex) {
            RequestScope.clear(); BaseContext.removeCurrentId();
            com.junzhou.security.RbacRequestContext.clear(request);
            MiniappResponses.error(response,HttpServletResponse.SC_FORBIDDEN,"门店不可用或无权访问");
            return false;
        } catch (DataAccessException ex) {
            RequestScope.clear(); BaseContext.removeCurrentId();
            com.junzhou.security.RbacRequestContext.clear(request);
            MiniappResponses.error(response,HttpServletResponse.SC_SERVICE_UNAVAILABLE,"服务暂不可用，请稍后重试");
            return false;
        }
    }
    @Override public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        RequestScope.clear();
        BaseContext.removeCurrentId();
        com.junzhou.security.RbacRequestContext.clear(request);
    }
    @Override public void afterConcurrentHandlingStarted(HttpServletRequest request, HttpServletResponse response, Object handler) {
        RequestScope.clear();
        BaseContext.removeCurrentId();
        com.junzhou.security.RbacRequestContext.clear(request);
    }
}
