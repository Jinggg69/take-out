package com.junzhou.platform;
import com.junzhou.context.BaseContext;
import com.junzhou.context.RequestScope;
import com.junzhou.properties.JwtProperties;
import org.springframework.stereotype.Component;
import org.springframework.dao.DataAccessException;
import org.springframework.web.servlet.HandlerInterceptor;
import javax.servlet.http.*;
@Component
public class PlatformAuthInterceptor implements HandlerInterceptor {
 private final PlatformAuthService auth;private final JwtProperties jwt;
 public PlatformAuthInterceptor(PlatformAuthService auth,JwtProperties jwt){this.auth=auth;this.jwt=jwt;}
 public boolean preHandle(HttpServletRequest req,HttpServletResponse res,Object handler){
  clear();try {Long id=auth.authenticate(req.getHeader(jwt.getAdminTokenName()));if(id==null){res.setStatus(401);return false;}PlatformContext.setAdminId(id);return true;}
  catch(DataAccessException failure){clear();res.setStatus(503);return false;}
 }
 public void afterCompletion(HttpServletRequest req,HttpServletResponse res,Object handler,Exception ex){clear();}
 private void clear(){PlatformContext.clear();BaseContext.removeCurrentId();RequestScope.clear();}
}
