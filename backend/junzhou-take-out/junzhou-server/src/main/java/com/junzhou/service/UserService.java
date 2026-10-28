package com.junzhou.service;

import com.junzhou.dto.UserLoginDTO;
import com.junzhou.entity.User;
import com.junzhou.result.Result;

public interface UserService {
    User wxLogin(UserLoginDTO userLoginDTO);
}
