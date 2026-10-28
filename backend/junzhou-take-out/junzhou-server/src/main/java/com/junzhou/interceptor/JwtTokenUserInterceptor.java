package com.junzhou.interceptor;

import com.junzhou.constant.JwtClaimsConstant;
import com.junzhou.context.BaseContext;
import com.junzhou.properties.JwtProperties;
import com.junzhou.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 校验小程序用户的登录令牌，并保存本次请求的用户 ID。
 */
@Component
public class JwtTokenUserInterceptor implements HandlerInterceptor {
    @Autowired
    private JwtProperties jwtProperties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        BaseContext.removeCurrentId();
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        try {
            String token = request.getHeader(jwtProperties.getUserTokenName());
            Claims claims = JwtUtil.parseJWT(jwtProperties.getUserSecretKey(), token);
            Long userId = Long.valueOf(claims.get(JwtClaimsConstant.USER_ID).toString());
            BaseContext.setCurrentId(userId);
            return true;
        } catch (Exception ex) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        BaseContext.removeCurrentId();
    }
}
