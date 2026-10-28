package com.junzhou.service.impl;

import com.junzhou.service.CartAccessService;

import com.junzhou.context.BaseContext;
import com.junzhou.exception.ShoppingCartBusinessException;
import com.junzhou.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** All cart writers lock the user row before their first consistent database read. */
@Service
public class CartAccessServiceImpl implements CartAccessService {
    @Autowired private UserMapper userMapper;

    /** {@inheritDoc} */
    @Override
    public Long currentUserId() {
        Long id = requireIdentity();
        if (userMapper.getById(id) == null) throw new ShoppingCartBusinessException("用户不存在，请重新登录");
        return id;
    }

    /** {@inheritDoc} */
    @Transactional(propagation = Propagation.MANDATORY)
    @Override
    public Long lockCurrentCart() {
        Long id = requireIdentity();
        if (userMapper.lockById(id) == null) throw new ShoppingCartBusinessException("用户不存在，请重新登录");
        return id;
    }

    /** 校验当前登录身份。 */
    private Long requireIdentity() {
        Long id = BaseContext.getCurrentId();
        if (id == null || id <= 0) throw new ShoppingCartBusinessException("请先登录");
        return id;
    }
}
