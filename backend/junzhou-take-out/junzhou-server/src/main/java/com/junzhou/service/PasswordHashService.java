package com.junzhou.service;

/** 员工密码的存储格式、验证以及存量记录升级策略。 */
public interface PasswordHashService {
    String encode(String password);
    boolean matches(String password, String storedHash);
    boolean needsUpgrade(String storedHash);
}
