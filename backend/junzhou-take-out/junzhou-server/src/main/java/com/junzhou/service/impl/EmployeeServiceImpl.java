package com.junzhou.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.junzhou.constant.MessageConstant;
import com.junzhou.constant.PasswordConstant;
import com.junzhou.constant.StatusConstant;
import com.junzhou.context.BaseContext;
import com.junzhou.dto.EmployeeDTO;
import com.junzhou.dto.EmployeeLoginDTO;
import com.junzhou.dto.EmployeePageQueryDTO;
import com.junzhou.dto.PasswordEditDTO;
import com.junzhou.entity.Employee;
import com.junzhou.exception.AccountLockedException;
import com.junzhou.exception.AccountNotFoundException;
import com.junzhou.exception.PasswordErrorException;
import com.junzhou.exception.PasswordEditFailedException;
import com.junzhou.mapper.EmployeeMapper;
import com.junzhou.mapper.AdminSessionMapper;
import com.junzhou.result.PageResult;
import com.junzhou.service.EmployeeService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.junzhou.service.PasswordHashService;
import com.junzhou.service.AdminSessionService;
import com.junzhou.vo.EmployeeLoginVO;
import com.junzhou.exception.LoginFailedException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


@Service
public class EmployeeServiceImpl implements EmployeeService {

    @Autowired
    private EmployeeMapper employeeMapper;

    @Autowired
    private AdminSessionMapper adminSessionMapper;

    @Autowired private com.junzhou.service.RbacService rbac;
    @Autowired private PasswordHashService passwordHashes;
    @Autowired private AdminSessionService adminSessions;

    @Override
    @Transactional
    public EmployeeLoginVO loginWithSession(EmployeeLoginDTO input) {
        Employee employee = login(input);
        String verified = employee.getPassword();
        String replacement = passwordHashes.needsUpgrade(verified) ? passwordHashes.encode(input.getPassword()) : null;
        // issue rechecks the credential while holding the tenant and employee row locks.
        String token = adminSessions.issue(employee);
        if (replacement != null && adminSessionMapper.upgradeVerifiedPassword(
                employee.getId(), employee.getTenantId(), verified, replacement) != 1) {
            throw new LoginFailedException("账号状态已变化，请重新登录");
        }
        return EmployeeLoginVO.builder().id(employee.getId()).userName(employee.getUsername())
                .name(employee.getName()).token(token).build();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void editPassword(PasswordEditDTO input) {
        Long employeeId = BaseContext.getCurrentId();
        if (employeeId == null || input == null
                || (input.getEmpId() != null && !employeeId.equals(input.getEmpId()))) {
            throw new PasswordEditFailedException("只能修改当前登录员工的密码");
        }
        if (input.getOldPassword() == null || input.getOldPassword().isEmpty()
                || input.getNewPassword() == null || !input.getNewPassword().matches("[0-9A-Za-z]{6,20}")) {
            throw new PasswordEditFailedException("请提供原密码和6至20位数字或字母的新密码");
        }
        String oldHash = employeeMapper.getCurrentPassword(employeeId);
        if (!passwordHashes.matches(input.getOldPassword(), oldHash)) {
            throw new PasswordEditFailedException("原密码错误或账号不可用，请重试");
        }
        String newHash = passwordHashes.encode(input.getNewPassword());
        // Compare-and-set prevents an old password from overwriting a concurrent password change.
        if (employeeMapper.updatePassword(employeeId, oldHash, newHash) != 1) {
            throw new PasswordEditFailedException("原密码错误或账号不可用，请重试");
        }
        adminSessionMapper.deleteByEmployeeId(employeeId);
    }

    /**
     * 员工登录
     *
     * @param employeeLoginDTO
     * @return
     */
    @Override
    public Employee login(EmployeeLoginDTO employeeLoginDTO) {
        String username = employeeLoginDTO.getUsername();
        String password = employeeLoginDTO.getPassword();

        //1、根据用户名查询数据库中的数据
        Employee employee = employeeMapper.getByUsername(username);

        //2、处理各种异常情况（用户名不存在、密码不对、账号被锁定）
        if (employee == null) {
            //账号不存在
            throw new AccountNotFoundException(MessageConstant.ACCOUNT_NOT_FOUND);
        }

        //密码比对
        if (!passwordHashes.matches(password, employee.getPassword())) {
            //密码错误
            throw new PasswordErrorException(MessageConstant.PASSWORD_ERROR);
        }

        if (employee.getStatus() == StatusConstant.DISABLE) {
            //账号被锁定
            throw new AccountLockedException(MessageConstant.ACCOUNT_LOCKED);
        }

        //3、返回实体对象
        return employee;
    }

    /**
     * 新增员工
     * @param employeeDTO
     */
    @Override
    public void save(EmployeeDTO employeeDTO) {
        Employee employee = new Employee();

        //对象属性拷贝
        BeanUtils.copyProperties(employeeDTO,employee);

        //设置账号状态，默认正常,1表示正常0表示锁定
        employee.setStatus(StatusConstant.ENABLE);

        //设置密码,默认为123456
        employee.setPassword(passwordHashes.encode(PasswordConstant.DEFAULT_PASSWORD));

        //设置当前记录的创建时间和修改时间
        // employee.setCreateTime(LocalDateTime.now());
        // employee.setUpdateTime(LocalDateTime.now());

        //设置当前记录创建人id和修改人id
        // employee.setCreateUser(BaseContext.getCurrentId());
        // employee.setUpdateUser(BaseContext.getCurrentId());

        employeeMapper.insert(employee);
    }

    /**
     * 员工分页查询
     * @param employeePageQueryDTO
     * @return
     */
    @Override
    public PageResult pageQuery(EmployeePageQueryDTO employeePageQueryDTO) {
        // select * from employee limit 0,10
        //开始分页查询
        PageHelper.startPage(employeePageQueryDTO.getPage(),employeePageQueryDTO.getPageSize());
        Page<Employee> page = employeeMapper.pageQuery(employeePageQueryDTO);
        long total = page.getTotal();
        List<Employee> records = page.getResult();
        return new PageResult(total,records);
    }

    /**
     * 启用禁用员工账号
     * @param status
     * @param id
     */
    @Override
    @Transactional
    public void startOrStop(Integer status, Long id){
        if(status==null || (status!=0 && status!=1))throw new SecurityException("Invalid status");
        rbac.lock();
        // update employee set status = ? where id = ?
        Employee employee = Employee.builder()
                .id(id)
                .status(status)
                .build();
        if (employeeMapper.update(employee) != 1) {
            throw new AccountNotFoundException(MessageConstant.ACCOUNT_NOT_FOUND);
        }
        rbac.ensureAdministrator();
        if (StatusConstant.DISABLE.equals(status)) {
            adminSessionMapper.deleteByEmployeeId(id);
        }
    }

    /**
     * 根据id查询员工信息
     * @param id
     * @return
     */
    @Override
    public Employee getById(Long id) {
        Employee employee = employeeMapper.getById(id);
        if (employee == null) throw new AccountNotFoundException(MessageConstant.ACCOUNT_NOT_FOUND);
        employee.setPassword("****");
        return employee;
    }

    /**
     * 编辑员工信息
     * @param employeeDTO
     */
    @Override
    public void update(EmployeeDTO employeeDTO) {
        Employee employee = new Employee();
        BeanUtils.copyProperties(employeeDTO,employee);
        // employee.setUpdateTime(LocalDateTime.now());
        // employee.setUpdateUser(BaseContext.getCurrentId());
        if (employeeMapper.update(employee) != 1) {
            throw new AccountNotFoundException(MessageConstant.ACCOUNT_NOT_FOUND);
        }
    }
}
