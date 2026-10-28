package com.junzhou.controller.admin;

import com.junzhou.context.BaseContext;
import com.junzhou.dto.EmployeeDTO;
import com.junzhou.dto.EmployeeLoginDTO;
import com.junzhou.dto.EmployeePageQueryDTO;
import com.junzhou.dto.PasswordEditDTO;
import com.junzhou.entity.Employee;
import com.junzhou.result.PageResult;
import com.junzhou.result.Result;
import com.junzhou.service.EmployeeService;
import com.junzhou.service.AdminSessionService;
import com.junzhou.vo.EmployeeLoginVO;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

/**
 * 员工管理
 */
@RestController
@RequestMapping("/admin/employee")
@Slf4j
public class EmployeeController {

    @Autowired
    private EmployeeService employeeService;
    @Autowired
    private AdminSessionService adminSessionService;

    /**
     * 登录
     *
     * @param employeeLoginDTO
     * @return
     */
    @PostMapping("/login")
    public Result<EmployeeLoginVO> login(@RequestBody EmployeeLoginDTO employeeLoginDTO) {
        Employee employee = employeeService.login(employeeLoginDTO);
        String token = adminSessionService.issue(employee);
        log.info("员工登录成功，employeeId={}", employee.getId());

        EmployeeLoginVO employeeLoginVO = EmployeeLoginVO.builder()
                .id(employee.getId())
                .userName(employee.getUsername())
                .name(employee.getName())
                .token(token)
                .build();

        return Result.success(employeeLoginVO);
    }

    /**
     * 退出
     *
     * @return
     */
    @PostMapping("/logout")
    public Result<String> logout(HttpServletRequest request) {
        adminSessionService.logout((String) request.getAttribute(AdminSessionService.SESSION_ATTRIBUTE), BaseContext.getCurrentId());
        return Result.success();
    }

    @PutMapping("/editPassword")
    @ApiOperation("修改当前登录员工密码")
    public Result<Void> editPassword(@RequestBody PasswordEditDTO passwordEditDTO) {
        employeeService.editPassword(passwordEditDTO);
        return Result.success();
    }

    @PostMapping
    @ApiOperation("新增员工")
    public Result save(@RequestBody EmployeeDTO employeeDTO) {
        log.info("新增员工：{}",employeeDTO);
        employeeService.save(employeeDTO);
        return Result.success();
    }

    @GetMapping("/page")
    @ApiOperation("员工分页查询")
    public Result<PageResult> page(EmployeePageQueryDTO employeePageQueryDTO){
        log.info("员工分页查询，参数为：{}",employeePageQueryDTO);
        PageResult pageResult = employeeService.pageQuery(employeePageQueryDTO);
        return Result.success(pageResult);
    }

    @PostMapping("/status/{status}")
    @ApiOperation("启用禁用员工账号")
    public Result startOrStop(@PathVariable Integer status,Long id) {
        log.info("启用禁用员工账号：{}{}",status,id);
        employeeService.startOrStop(status,id);
        return Result.success();
    }

    @GetMapping("/{id}")
    @ApiOperation("根据id查询员工信息")
    public Result<Employee> getById(@PathVariable Long id) {
        log.info("根据id查询员工信息:{}",id);
        Employee employee = employeeService.getById(id);
        return Result.success(employee);
    }

    @PutMapping
    @ApiOperation("编辑员工信息")
    public Result update(@RequestBody EmployeeDTO employeeDTO){
        log.info("编辑员工信息:{}",employeeDTO);
        employeeService.update(employeeDTO);
        return Result.success();
    }
}
