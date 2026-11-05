package com.junzhou.interceptor;
import com.junzhou.security.*;
import com.junzhou.service.RbacService;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import javax.servlet.http.*;
@Component public class RbacInterceptor implements HandlerInterceptor {
 private final RbacService rbac;
 public RbacInterceptor(RbacService rbac){this.rbac=rbac;}
 public boolean preHandle(HttpServletRequest req,HttpServletResponse res,Object handler){
  if(!(handler instanceof HandlerMethod)){res.setStatus(403);return false;}
  RequireActions a=((HandlerMethod)handler).getMethodAnnotation(RequireActions.class);
  if(a==null){res.setStatus(403);return false;}
  try{for(PermissionAction action:a.value())rbac.require(action);return true;}
  catch(SecurityException ex){res.setStatus(403);return false;}
  catch(org.springframework.dao.DataAccessException ex){res.setStatus(503);return false;}
 }
}
