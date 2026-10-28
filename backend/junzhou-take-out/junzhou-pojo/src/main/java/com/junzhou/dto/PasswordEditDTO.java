package com.junzhou.dto;

import lombok.Data;
import lombok.ToString;

import java.io.Serializable;

@Data
public class PasswordEditDTO implements Serializable {

    //员工id
    private Long empId;

    //旧密码
    @ToString.Exclude
    private String oldPassword;

    //新密码
    @ToString.Exclude
    private String newPassword;

}
