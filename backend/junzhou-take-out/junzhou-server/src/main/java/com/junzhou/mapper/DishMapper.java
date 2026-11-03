package com.junzhou.mapper;

import com.github.pagehelper.Page;
import com.junzhou.annotation.AutoFill;
import com.junzhou.dto.DishPageQueryDTO;
import com.junzhou.entity.Dish;
import com.junzhou.entity.DishFlavor;
import com.junzhou.enumeration.OperationType;
import com.junzhou.vo.DishVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DishMapper {

    /**
     * 根据分类id查询菜品数量
     * @param categoryId
     * @return
     */
    @Select("<script><bind name=\"scopeTenant\" value=\"@com.junzhou.context.RequestScope@requireTenantId()\"/>select count(id) from dish where category_id = #{categoryId} and tenant_id = #{tenantId} and tenant_id = #{scopeTenant}</script>")
    Integer countByCategoryId(@Param("categoryId") Long categoryId, @Param("tenantId") Long tenantId);

    @AutoFill(OperationType.INSERT)
    int insert(Dish dish);

    /**
     * 批量插入菜品口味
     */
    int insertFlavors(@Param("flavors") List<DishFlavor> flavors);

    /**
     * 菜品分页查询
     * @param dishPageQueryDTO
     * @return
     */
    Page<DishVO> pageQuery(DishPageQueryDTO dishPageQueryDTO);

    /**
     * 根据id删除菜品
     * @param ids
     */
    void deleteBatch(@Param("ids") List<Long> ids);

    /**
     * 根据id查询菜品
     * @param id
     * @return
     */
    @Select("<script><bind name=\"scopeTenant\" value=\"@com.junzhou.context.RequestScope@requireTenantId()\"/>select * from dish where id = #{id} and tenant_id = #{scopeTenant}</script>")
    Dish getById(Long id);

    /** Lock an authorized row for a transactional state change. */
    @Select("<script><bind name=\"scopeTenant\" value=\"@com.junzhou.context.RequestScope@requireTenantId()\"/>select * from dish where id = #{id} and tenant_id = #{scopeTenant} for update</script>")
    Dish getByIdForUpdate(Long id);

    /**
     * 根据id更新菜品及修改记录
     */
    @AutoFill(OperationType.UPDATE)
    int updateById(Dish dish);

    /**
     * 按分类和售卖状态查询菜品
     */
    List<Dish> list(Dish dish);
}
