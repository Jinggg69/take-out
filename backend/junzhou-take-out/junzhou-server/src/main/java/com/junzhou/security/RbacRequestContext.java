package com.junzhou.security;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.*;
/** Request attributes only: no application-owned ThreadLocal or cross-request cache. */
public final class RbacRequestContext {
 private static final String ENABLED=RbacRequestContext.class.getName()+".enabled", SNAPSHOT=ENABLED+".snapshot";
 private RbacRequestContext(){}
 public static void begin(HttpServletRequest request){clear(request);request.setAttribute(ENABLED,Boolean.TRUE);}
 public static void clear(HttpServletRequest request){request.removeAttribute(ENABLED);request.removeAttribute(SNAPSHOT);}
 private static HttpServletRequest request(){RequestAttributes a=RequestContextHolder.getRequestAttributes();return a instanceof ServletRequestAttributes?((ServletRequestAttributes)a).getRequest():null;}
 public static AuthorizationSnapshot current(){HttpServletRequest r=request();return r!=null&&Boolean.TRUE.equals(r.getAttribute(ENABLED))?(AuthorizationSnapshot)r.getAttribute(SNAPSHOT):null;}
 public static void store(AuthorizationSnapshot snapshot){HttpServletRequest r=request();if(r!=null&&Boolean.TRUE.equals(r.getAttribute(ENABLED))&&r.getAttribute(SNAPSHOT)==null)r.setAttribute(SNAPSHOT,snapshot);}
 public static void invalidate(){HttpServletRequest r=request();if(r!=null)r.removeAttribute(SNAPSHOT);}
 public static void invalidateAfterTransaction(){
  invalidate();final HttpServletRequest r=request();
  if(r!=null&&org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive())
   org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization(){
    @Override public void afterCompletion(int status){r.removeAttribute(SNAPSHOT);}
   });
 }
}
