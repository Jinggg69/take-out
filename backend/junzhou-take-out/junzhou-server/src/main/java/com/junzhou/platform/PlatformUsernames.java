package com.junzhou.platform;

import java.util.Locale;
public final class PlatformUsernames {
 private PlatformUsernames() { }
 public static boolean reserved(String username) { return username!=null && username.trim().toLowerCase(Locale.ROOT).startsWith("platform_"); }
 public static void requireEmployee(String username) { if(reserved(username))throw new com.junzhou.exception.LoginFailedException("platform_为平台账号保留前缀"); }
}
