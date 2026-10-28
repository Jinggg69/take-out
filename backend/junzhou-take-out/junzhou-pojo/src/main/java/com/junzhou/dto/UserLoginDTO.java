package com.junzhou.dto;

import lombok.Data;
import lombok.ToString;

import java.io.Serializable;

/**
 * C端用户登录
 */
@Data
public class UserLoginDTO implements Serializable {

    @ToString.Exclude
    private String code;

}
