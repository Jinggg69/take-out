package com.junzhou.mapper;


import com.junzhou.entity.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper {
    @Select("select id from `user` where id=#{id} for update")
    Long lockById(Long id);

    /**
     * 根据openid查询用户
     * @param openid
     * @return
     */
    @Select("select * from user where openid = #{openid}")
    User getByOpenid(String openid);

    /**
     * 根据用户 ID 查询微信支付所需的 openid。
     */
    @Select("select * from user where id = #{id}")
    User getById(Long id);

    /**
     * 插入数据
     * @param user
     */
    void insert(User user);
}
