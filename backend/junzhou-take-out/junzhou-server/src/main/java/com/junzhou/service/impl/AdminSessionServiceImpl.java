package com.junzhou.service.impl;

import com.junzhou.service.AdminSessionService;

import com.junzhou.constant.JwtClaimsConstant;
import com.junzhou.constant.StatusConstant;
import com.junzhou.entity.Employee;
import com.junzhou.exception.LoginFailedException;
import com.junzhou.mapper.AdminSessionMapper;
import com.junzhou.mapper.EmployeeMapper;
import com.junzhou.properties.JwtProperties;
import com.junzhou.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class AdminSessionServiceImpl implements AdminSessionService {

    @Autowired private AdminSessionMapper adminSessionMapper;
    @Autowired private EmployeeMapper employeeMapper;
    @Autowired private JwtProperties jwtProperties;

    /** {@inheritDoc} */
    @Transactional
    @Override
    public String issue(Employee authenticated) {
        Employee current = employeeMapper.getByIdForUpdate(authenticated.getId());
        if (current == null || !StatusConstant.ENABLE.equals(current.getStatus()) || current.getPassword() == null
                || !Objects.equals(current.getPassword(), authenticated.getPassword())) {
            throw new LoginFailedException("账号状态已变化，请重新登录");
        }
        if (jwtProperties.getAdminTtl() <= 0) throw new IllegalStateException("管理端令牌有效期必须大于零");
        String sessionId = UUID.randomUUID().toString();
        Map<String,Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.EMP_ID, current.getId());
        claims.put("jti", sessionId);
        String token = JwtUtil.createJWT(jwtProperties.getAdminSecretKey(), jwtProperties.getAdminTtl(), claims);
        long expiresAt = JwtUtil.parseJWT(jwtProperties.getAdminSecretKey(), token).getExpiration().getTime();
        long now = System.currentTimeMillis();
        if (expiresAt <= now) throw new IllegalStateException("管理端令牌有效期过短");
        adminSessionMapper.deleteExpired(current.getId(), now);
        if (adminSessionMapper.insert(sessionId, current.getId(), expiresAt) != 1) {
            throw new IllegalStateException("认证会话创建失败");
        }
        return token;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isActive(String sessionId, Long employeeId) {
        return adminSessionMapper.countActive(sessionId, employeeId, System.currentTimeMillis()) == 1;
    }

    /** {@inheritDoc} */
    @Override
    public void logout(String sessionId, Long employeeId) {
        if (sessionId == null || employeeId == null) throw new LoginFailedException("请先登录");
        adminSessionMapper.deleteCurrent(sessionId, employeeId);
    }
}
