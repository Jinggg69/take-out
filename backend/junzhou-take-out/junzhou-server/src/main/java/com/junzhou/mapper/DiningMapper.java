package com.junzhou.mapper;

import com.junzhou.entity.DiningTable;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface DiningMapper {
    String GUARD="<script><bind name='scopeTenant' value='@com.junzhou.context.RequestScope@requireTenantId()'/><bind name='scopeStore' value='@com.junzhou.context.RequestScope@requireStoreId()'/>";
    String SCOPE=" tenant_id=#{scopeTenant} and store_id=#{scopeStore} ";
    @Select(GUARD+"select * from store_dining_table where "+SCOPE+" order by id"+"</script>")
    List<DiningTable> list(@Param("tenant") Long tenant,@Param("store") Long store);
    @Select(GUARD+"select * from store_dining_table where id=#{id} and "+SCOPE+" for update"+"</script>")
    DiningTable lock(@Param("tenant") Long tenant,@Param("store") Long store,@Param("id") Long id);
    // Capability lookup: server resolves ownership from the opaque token, never from supplied IDs.
    @Select("select d.*,s.name store_name from store_dining_table d join store s on s.id=d.store_id and s.tenant_id=d.tenant_id join tenant t on t.id=s.tenant_id where d.qr_token=#{token} and d.enabled=1 and s.status=1 and t.status=1")
    DiningTable resolve(String token);
    @Insert(GUARD+"insert into store_dining_table(tenant_id,store_id,name,enabled,qr_token) values(#{scopeTenant},#{scopeStore},#{name},#{enabled},#{qrToken})"+"</script>")
    @Options(useGeneratedKeys=true,keyProperty="id") void create(DiningTable table);
    @Update(GUARD+"update store_dining_table set name=#{name},enabled=#{enabled},qr_token=#{qrToken},current_session_id=#{currentSessionId} where id=#{id} and tenant_id=#{scopeTenant} and store_id=#{scopeStore}"+"</script>")
    int update(DiningTable table);
    @Insert(GUARD+"insert into dining_session(id,tenant_id,store_id,table_id,table_snapshot,opened_at) values(#{currentSessionId},#{scopeTenant},#{scopeStore},#{id},#{name},current_timestamp)"+"</script>")
    void open(DiningTable table);
    @Insert(GUARD+"<bind name='scopeUser' value='@com.junzhou.context.RequestScope@requireUserId()'/>insert into dining_session_member(session_id,user_id) select #{session},#{scopeUser} where exists(select 1 from dining_session where id=#{session} and tenant_id=#{scopeTenant} and store_id=#{scopeStore} and closed_at is null) and not exists(select 1 from dining_session_member where session_id=#{session} and user_id=#{scopeUser})"+"</script>")
    void join(@Param("session") String session,@Param("user") Long user);
    @Select(GUARD+"select count(*) from dining_session ds join dining_session_member m on m.session_id=ds.id where ds.id=#{session} and ds.closed_at is null and ds.tenant_id=#{scopeTenant} and ds.store_id=#{scopeStore} and m.user_id=#{user}"+"</script>")
    int member(@Param("session") String session,@Param("tenant") Long tenant,@Param("store") Long store,@Param("user") Long user);
    @Select(GUARD+"select count(*) from orders where dining_session_id=#{session} and tenant_id=#{scopeTenant} and store_id=#{scopeStore} and status not in (5,6)"+"</script>")
    int unfinished(@Param("session") String session,@Param("tenant") Long tenant,@Param("store") Long store);
    @Update(GUARD+"update dining_session set closed_at=current_timestamp where id=#{session} and tenant_id=#{scopeTenant} and store_id=#{scopeStore} and closed_at is null"+"</script>")
    int close(@Param("session") String session,@Param("tenant") Long tenant,@Param("store") Long store);
}
