package com.junzhou.platform;

/** Platform identity is deliberately independent of employee/store request scope. */
public final class PlatformContext {
 private static final ThreadLocal<Long> ADMIN = new ThreadLocal<>();
 private PlatformContext() { }
 public static Long requireAdminId() { Long id=ADMIN.get(); if(id==null)throw new SecurityException("平台管理员未认证"); return id; }
 public static void setAdminId(Long id) { ADMIN.set(id); }
 public static void clear() { ADMIN.remove(); }
}
