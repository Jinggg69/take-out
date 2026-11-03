package com.junzhou.mapper;

import com.github.pagehelper.Page;
import com.junzhou.annotation.AutoFill;
import com.junzhou.enumeration.OperationType;
import com.junzhou.dto.CategoryPageQueryDTO;
import com.junzhou.entity.Category;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface CategoryMapper {

    @Select("<script><bind name=\"scopeTenant\" value=\"@com.junzhou.context.RequestScope@requireTenantId()\"/>" + "select count(*) from category where id=#{id} and tenant_id=#{tenantId} and tenant_id=#{scopeTenant}" + "</script>")
    int countOwned(@Param("id") Long id, @Param("tenantId") Long tenantId);

    /** Lock the parent category before changing catalog relationships. */
    @Select("<script><bind name=\"scopeTenant\" value=\"@com.junzhou.context.RequestScope@requireTenantId()\"/>select id from category where id=#{id} and tenant_id=#{tenantId} and tenant_id=#{scopeTenant} for update</script>")
    Long lockOwned(@Param("id") Long id, @Param("tenantId") Long tenantId);

    /**
     * 插入数据
     * @param category
     */
    @Insert("<script><bind name=\"scopeTenant\" value=\"@com.junzhou.context.RequestScope@requireTenantId()\"/>" + "insert into category(tenant_id, type, name, sort, status, create_time, update_time, create_user, update_user)" +
            " SELECT" +
            " #{scopeTenant}, #{category.type}, #{category.name}, #{category.sort}, #{category.status}, #{category.createTime}, #{category.updateTime}, #{category.createUser}, #{category.updateUser} where #{tenantId} = #{scopeTenant}" + "</script>")
    @AutoFill(OperationType.INSERT)
    int insert(@Param("category") Category category, @Param("tenantId") Long tenantId);

    /**
     * 分页查询
     * @param categoryPageQueryDTO
     * @return
     */
    Page<Category> pageQuery(@Param("query") CategoryPageQueryDTO categoryPageQueryDTO, @Param("tenantId") Long tenantId);

    /**
     * 根据id删除分类
     * @param id
     */
    @Delete("<script><bind name=\"scopeTenant\" value=\"@com.junzhou.context.RequestScope@requireTenantId()\"/>" + "delete from category where id = #{id} and tenant_id = #{tenantId} and tenant_id=#{scopeTenant}" + "</script>")
    int deleteById(@Param("id") Long id, @Param("tenantId") Long tenantId);

    /**
     * 根据id修改分类
     * @param category
     */
    @AutoFill(OperationType.UPDATE)
    int update(@Param("category") Category category, @Param("tenantId") Long tenantId);

    /**
     * 根据类型查询分类
     * @param type
     * @return
     */
    List<Category> list(@Param("type") Integer type, @Param("tenantId") Long tenantId);
}
