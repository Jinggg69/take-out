package com.junzhou.service;

import com.junzhou.dto.EmployeeDTO;
import com.junzhou.dto.EmployeeLoginDTO;
import com.junzhou.dto.EmployeePageQueryDTO;
import com.junzhou.dto.PasswordEditDTO;
import com.junzhou.entity.Employee;
import com.junzhou.result.PageResult;

public interface EmployeeService {

    /** 修改当前认证员工密码，客户端不能选择其他员工。 */
    void editPassword(PasswordEditDTO passwordEditDTO);

    /**
     * 员工登录
     * @param employeeLoginDTO
     * @return
     */
    Employee login(EmployeeLoginDTO employeeLoginDTO);

    /**
     * 新增员工
     * @param employeeDTO
     */
    void save(EmployeeDTO employeeDTO);

    /**
     * 员工分页查询
     *
     * @param employeePageQueryDTO
     * @return
     */
    PageResult pageQuery(EmployeePageQueryDTO employeePageQueryDTO);

    /**
     * 启用禁用员工账号
     * @param status
     * @param id
     */
    void startOrStop(Integer status, Long id);

    /**
     * 根据id查询员工信息
     * @param id
     * @return
     */
    Employee getById(Long id);

    /**
     * 编辑员工信息
     * @param employeeDTO
     */
    void update(EmployeeDTO employeeDTO);
}
