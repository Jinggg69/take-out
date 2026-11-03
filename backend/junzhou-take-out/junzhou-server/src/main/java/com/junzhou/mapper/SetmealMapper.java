package com.junzhou.mapper;

import com.github.pagehelper.Page;
import com.junzhou.annotation.AutoFill;
import com.junzhou.dto.SetmealPageQueryDTO;
import com.junzhou.entity.Setmeal;
import com.junzhou.enumeration.OperationType;
import com.junzhou.vo.DishItemVO;
import com.junzhou.vo.SetmealVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SetmealMapper {

    /**
     * 根据分类id查询套餐的数量
     * @param categoryId
     * @return
     */
    @Select("<script><bind name=\"scopeTenant\" value=\"@com.junzhou.context.RequestScope@requireTenantId()\"/>select count(id) from setmeal where category_id = #{categoryId} and tenant_id = #{tenantId} and tenant_id = #{scopeTenant}</script>")
    Integer countByCategoryId(@Param("categoryId") Long categoryId, @Param("tenantId") Long tenantId);

    /**
     * 新增套餐，回填主键并自动填充公共字段
     */
    @AutoFill(OperationType.INSERT)
    int insert(Setmeal setmeal);

    /**
     * 套餐分页查询
     */
    Page<SetmealVO> pageQuery(SetmealPageQueryDTO setmealPageQueryDTO);

    @Select("<script><bind name=\"scopeTenant\" value=\"@com.junzhou.context.RequestScope@requireTenantId()\"/>select * from setmeal where id = #{id} and tenant_id = #{scopeTenant}</script>")
    Setmeal getById(Long id);

    /** Lock an authorized row for a transactional state change. */
    @Select("<script><bind name=\"scopeTenant\" value=\"@com.junzhou.context.RequestScope@requireTenantId()\"/>select * from setmeal where id = #{id} and tenant_id = #{scopeTenant} for update</script>")
    Setmeal getByIdForUpdate(Long id);

    /**
     * 根据id查询套餐和分类名称
     */
    SetmealVO getVOById(Long id);

    @AutoFill(OperationType.UPDATE)
    int updateById(Setmeal setmeal);

    void deleteBatch(@Param("ids") List<Long> ids);

    /**
     * 动态条件查询套餐
     */
    List<Setmeal> list(Setmeal setmeal);

    /**
     * 根据套餐id查询菜品选项
     */
    @Select("<script><bind name=\"scopeTenant\" value=\"@com.junzhou.context.RequestScope@requireTenantId()\"/>select sd.name, sd.copies, d.image, d.description " +
            "from setmeal_dish sd join setmeal s on s.id = sd.setmeal_id join dish d on sd.dish_id = d.id and d.tenant_id = s.tenant_id " +
            "where sd.setmeal_id = #{setmealId} and s.tenant_id = #{scopeTenant} and not exists (select 1 from setmeal_dish invalid left join dish valid on valid.id = invalid.dish_id and valid.tenant_id = #{scopeTenant} where invalid.setmeal_id = s.id and valid.id is null)</script>")
    List<DishItemVO> getDishItemBySetmealId(Long setmealId);
}
