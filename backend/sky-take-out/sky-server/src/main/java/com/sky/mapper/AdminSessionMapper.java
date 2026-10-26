package com.sky.mapper;

import org.apache.ibatis.annotations.*;

/** Stores session identifiers, never bearer tokens or passwords. */
@Mapper
public interface AdminSessionMapper {
    @Insert("insert into admin_session (session_id,employee_id,expires_at) values (#{sessionId},#{employeeId},#{expiresAt})")
    int insert(@Param("sessionId") String sessionId, @Param("employeeId") Long employeeId,
               @Param("expiresAt") long expiresAt);

    @Select("select count(*) from admin_session s join employee e on e.id=s.employee_id "
            + "where s.session_id=#{sessionId} and s.employee_id=#{employeeId} "
            + "and s.expires_at>#{now} and e.status=1")
    int countActive(@Param("sessionId") String sessionId, @Param("employeeId") Long employeeId,
                    @Param("now") long now);

    @Delete("delete from admin_session where session_id=#{sessionId} and employee_id=#{employeeId}")
    int deleteCurrent(@Param("sessionId") String sessionId, @Param("employeeId") Long employeeId);

    @Delete("delete from admin_session where employee_id=#{employeeId}")
    int deleteByEmployeeId(Long employeeId);

    @Delete("delete from admin_session where employee_id=#{employeeId} and expires_at<=#{now}")
    int deleteExpired(@Param("employeeId") Long employeeId, @Param("now") long now);
}
