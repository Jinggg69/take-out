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
    @Select("select count(id) from dish where category_id = #{categoryId}")
    Integer countByCategoryId(Long categoryId);

    @AutoFill(OperationType.INSERT)
    void insert(Dish dish);

    /**
     * 批量插入菜品口味
     */
    void insertFlavors(@Param("flavors") List<DishFlavor> flavors);

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
    @Select("select * from dish where id = #{id}")
    Dish getById(Long id);

    /**
     * 根据id更新菜品及修改记录
     */
    @AutoFill(OperationType.UPDATE)
    void updateById(Dish dish);

    /**
     * 按分类和售卖状态查询菜品
     */
    List<Dish> list(Dish dish);
}
