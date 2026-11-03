package com.junzhou.mapper;

import com.junzhou.entity.AddressBook;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface AddressBookMapper {

    /**
     * 条件查询
     * @param addressBook
     * @return
     */
    List<AddressBook> list(AddressBook addressBook);

    /**
     * 新增
     * @param addressBook
     */
    @Insert("<script><bind name='_user' value='@com.junzhou.context.RequestScope@requireUserId()'/> insert into address_book" +
            "        (user_id, consignee, phone, sex, province_code, province_name, city_code, city_name, district_code," +
            "         district_name, detail, label, is_default)" +
            "        values (#{_user}, #{consignee}, #{phone}, #{sex}, #{provinceCode}, #{provinceName}, #{cityCode}, #{cityName}," +
            "                #{districtCode}, #{districtName}, #{detail}, #{label}, #{isDefault})</script>")
    void insert(AddressBook addressBook);

    @Select("<script><bind name='_user' value='@com.junzhou.context.RequestScope@requireUserId()'/> select * from address_book where id = #{id} and user_id = #{userId} and user_id=#{_user}</script>")
    AddressBook getByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * 根据id修改
     * @param addressBook
     */
    int update(AddressBook addressBook);

    /**
     * 根据 用户id修改 是否默认地址
     * @param addressBook
     */
    @Update("<script><bind name='_user' value='@com.junzhou.context.RequestScope@requireUserId()'/> update address_book set is_default = #{isDefault} where user_id = #{userId} and user_id=#{_user}</script>")
    void updateIsDefaultByUserId(AddressBook addressBook);

    @Update("<script><bind name='_user' value='@com.junzhou.context.RequestScope@requireUserId()'/> update address_book set is_default = 1 where id = #{id} and user_id = #{userId} and user_id=#{_user}</script>")
    int setDefaultByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * 根据id删除地址
     * @param id
     */
    @Delete("<script><bind name='_user' value='@com.junzhou.context.RequestScope@requireUserId()'/> delete from address_book where id = #{id} and user_id = #{userId} and user_id=#{_user}</script>")
    int deleteByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

}
