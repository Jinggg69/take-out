package com.junzhou.mapper;

import com.github.pagehelper.Page;
import com.junzhou.annotation.AutoFill;
import com.junzhou.dto.EmployeePageQueryDTO;
import com.junzhou.entity.Employee;
import com.junzhou.enumeration.OperationType;
import com.junzhou.result.PageResult;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface EmployeeMapper {

    @Select("<script><bind name='_tenant' value='@com.junzhou.context.RequestScope@requireAuthenticatedEmployeeTenantId()'/> select password from employee where id=#{empId} and tenant_id=#{_tenant} and status=1</script>")
    String getCurrentPassword(Long empId);

    @Update("<script><bind name='_tenant' value='@com.junzhou.context.RequestScope@requireAuthenticatedEmployeeTenantId()'/> update employee set password=#{newPassword}, update_time=current_timestamp, update_user=#{empId} "
            + "where id=#{empId} and tenant_id=#{_tenant} and password=#{oldPassword} and status=1</script>")
    int updatePassword(@Param("empId") Long empId, @Param("oldPassword") String oldPassword,
                       @Param("newPassword") String newPassword);

    /**
     * 认证引导专用：按全局唯一用户名定位员工，由调用方验证密码
     * @param username 用户名
     * @return 查询到的员工对象，不存在时返回 null
     */
    @Select("select * from employee where username = #{username}")
    Employee getByUsername(String username);

    /**
     * 插入员工数据
     * @param employee 要保存的员工信息
     */
    @Insert("<script><bind name='_tenant' value='@com.junzhou.context.RequestScope@requireEmployeeTenantId()'/> insert into employee (tenant_id,name,username,password,phone,sex,id_number,status,create_time,update_time,create_user,update_user) " +
            "values (#{_tenant},#{name},#{username},#{password},#{phone},#{sex},#{idNumber},#{status},#{createTime},#{updateTime},#{createUser},#{updateUser})</script>")
    @AutoFill(value = OperationType.INSERT)
    void insert(Employee employee);

    /**
     * 员工分页查询
     * @param employeePageQueryDTO
     * @return
     */
    Page<Employee> pageQuery(EmployeePageQueryDTO employeePageQueryDTO);

    /**
     * 根据主键动态修改属性
     * @param employee
     */
    @AutoFill(value = OperationType.UPDATE)
    int update(Employee employee);

    /**
     * 根据id查询员工信息
     * @param id
     * @return
     */
    @Select("<script><bind name='_tenant' value='@com.junzhou.context.RequestScope@requireEmployeeTenantId()'/> select * from employee where id = #{id} and tenant_id=#{_tenant}</script>")
    Employee getById(Long id);

    /** Authentication bootstrap: locks and rechecks the verified credential. */
    @Select("select * from employee where id=#{id} and password=#{password} and status=1 for update")
    Employee getForSessionIssue(@Param("id") Long id, @Param("password") String password);
}
