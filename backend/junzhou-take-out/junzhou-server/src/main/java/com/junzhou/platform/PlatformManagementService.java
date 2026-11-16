package com.junzhou.platform;

import com.junzhou.exception.BaseException;
import com.junzhou.result.PageResult;
import com.junzhou.security.PermissionAction;
import com.junzhou.service.PasswordHashService;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.*;

/** Platform-only administration; never installs or derives a merchant RequestScope. */
@Service
@Transactional(rollbackFor=Exception.class)
public class PlatformManagementService {
    private final JdbcTemplate db;
    private final PasswordHashService passwords;
    private final Environment environment;
    public PlatformManagementService(JdbcTemplate db,PasswordHashService passwords,Environment environment) {
        this.db=db; this.passwords=passwords; this.environment=environment;
    }
    public PageResult tenants(int page,int pageSize,String keyword) {
        PlatformContext.requireAdminId(); checkPage(page,pageSize);
        String pattern=pattern(keyword);
        long total=db.queryForObject("select count(*) from tenant where deleted_at is null and (code like ? or name like ?)",Long.class,pattern,pattern);
        List<Map<String,Object>> rows=db.query("select t.id,t.code,t.name,t.status,(select count(*) from store s where s.tenant_id=t.id and s.deleted_at is null) store_count from tenant t where t.deleted_at is null and (t.code like ? or t.name like ?) order by t.id desc limit ? offset ?",(rs,n)->{
            Map<String,Object> m=new LinkedHashMap<>();m.put("id",rs.getLong("id"));m.put("code",rs.getString("code"));m.put("name",rs.getString("name"));m.put("status",rs.getInt("status"));m.put("storeCount",rs.getLong("store_count"));return m;
        },pattern,pattern,pageSize,((long)page-1)*pageSize);
        return new PageResult(total,rows);
    }
    public PageResult stores(int page,int pageSize,String keyword,Long tenantId) {
        PlatformContext.requireAdminId(); checkPage(page,pageSize);String p=pattern(keyword);
        String where=" from store s join tenant t on t.id=s.tenant_id left join store_settings c on c.tenant_id=s.tenant_id and c.store_id=s.id where s.deleted_at is null and t.deleted_at is null and (s.code like ? or s.name like ?)";
        List<Object> args=new ArrayList<>(Arrays.asList(p,p)); if(tenantId!=null){where+=" and s.tenant_id=?";args.add(tenantId);}
        long total=db.queryForObject("select count(*)"+where,args.toArray(),Long.class);
        args.add(pageSize);args.add(((long)page-1)*pageSize);
        List<Map<String,Object>> rows=db.query("select s.id,s.tenant_id,t.name tenant_name,s.code,s.name,s.status,c.address,c.latitude,c.longitude"+where+" order by s.id desc limit ? offset ?",args.toArray(),(rs,n)->{
            Map<String,Object> m=new LinkedHashMap<>();m.put("id",rs.getLong("id"));m.put("tenantId",rs.getLong("tenant_id"));m.put("tenantName",rs.getString("tenant_name"));m.put("code",rs.getString("code"));m.put("name",rs.getString("name"));m.put("status",rs.getInt("status"));m.put("address",rs.getString("address"));m.put("latitude",rs.getBigDecimal("latitude"));m.put("longitude",rs.getBigDecimal("longitude"));return m;
        });return new PageResult(total,rows);
    }
    public Long createTenant(Map<String,Object> form) {
        PlatformContext.requireAdminId();String code=code(form),name=text(form,"name",100);
        String username=text(form,"adminUsername",32);PlatformUsernames.requireEmployee(username);
        Object rawPassword=form.get("adminPassword");if(!(rawPassword instanceof String)||((String)rawPassword).length()<8||((String)rawPassword).length()>128)throw bad("管理员密码需为8至128位");String password=(String)rawPassword;
        String adminName=text(form,"adminName",32);
        Object store=form.get("store");if(!(store instanceof Map))throw bad("请填写首家门店");
        Map<String,Object> first=(Map<String,Object>)store;validateStore(first);
        Long id=insert("insert into tenant(code,name,status) values(?,?,1)",code,name);
        Long storeId=insertStore(id,first);
        String[] roles={"MERCHANT_ADMIN","MANAGER","CLERK"};String[] names={"商户管理员","店长","店员"};
        for(int i=0;i<roles.length;i++) db.update("insert into rbac_role(tenant_id,code,name) values(?,?,?)",id,roles[i],names[i]);
        for(PermissionAction action:PermissionAction.values()) {
            String permission=action.getCode();db.update("insert into rbac_permission(tenant_id,code,name) values(?,?,?)",id,permission,permission);
            grant(id,"MERCHANT_ADMIN",permission);
            if(Arrays.asList("dish:read","dish:update","order:read","order:accept","report:read").contains(permission))grant(id,"MANAGER",permission);
            if(Arrays.asList("dish:read","order:read","order:accept").contains(permission))grant(id,"CLERK",permission);
        }
        Long employee=insert("insert into employee(tenant_id,username,password,name,phone,sex,id_number,status,create_time,update_time) values(?,?,?,?,?,?,?,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",id,username,passwords.encode(password),adminName,"","","");
        db.update("insert into employee_role(tenant_id,employee_id,role_code,data_scope) values(?,?,'MERCHANT_ADMIN','TENANT_STORES')",id,employee);
        db.update("insert into employee_store(tenant_id,employee_id,store_id) values(?,?,?)",id,employee,storeId);
        audit("CREATE","TENANT",id);return id;
    }
    public Long createStore(Map<String,Object> form) {
        PlatformContext.requireAdminId();Long tenant=number(form.get("tenantId"));Map<String,Object> owner=lockTenant(tenant);if(((Number)owner.get("status")).intValue()!=1)throw bad("请先启用商户再添加门店");validateStore(form);
        Long id=insertStore(tenant,form);
        // All tenant-wide administrators need explicit store membership as well as data scope.
        db.update("insert into employee_store(tenant_id,employee_id,store_id) select distinct er.tenant_id,er.employee_id,? from employee_role er join employee e on e.id=er.employee_id and e.tenant_id=er.tenant_id where er.tenant_id=? and er.role_code='MERCHANT_ADMIN' and er.data_scope='TENANT_STORES' and e.status=1",id,tenant);
        audit("CREATE","STORE",id);return id;
    }
    public void updateTenant(Long id,Map<String,Object> form) {
        PlatformContext.requireAdminId();lockTenant(id);
        db.update("update tenant set code=?,name=? where id=?",code(form),text(form,"name",100),id);audit("UPDATE","TENANT",id);
    }
    public void updateStore(Long id,Map<String,Object> form) {
        PlatformContext.requireAdminId();Long tenant=tenantForStore(id);lockTenant(tenant);lockStore(tenant,id);validateStore(form);
        if(form.containsKey("tenantId")&&!tenant.equals(number(form.get("tenantId"))))throw bad("门店不能转移至其他商户");
        db.update("update store set code=?,name=? where id=? and tenant_id=?",code(form),text(form,"name",100),id,tenant);
        saveSettings(tenant,id,form);audit("UPDATE","STORE",id);
    }
    public void tenantStatus(Long id,Integer status) {
        PlatformContext.requireAdminId();checkStatus(status);lockTenant(id);lockTenantStores(id);
        if(status==0)db.update("update store set status=0 where tenant_id=? and deleted_at is null",id);
        db.update("update tenant set status=? where id=?",status,id);revoke(id);audit("STATUS_"+status,"TENANT",id);
    }
    public void storeStatus(Long id,Integer status) {
        PlatformContext.requireAdminId();checkStatus(status);Long tenant=tenantForStore(id);Map<String,Object> t=lockTenant(tenant);lockStore(tenant,id);
        if(status==1&&((Number)t.get("status")).intValue()!=1)throw bad("请先启用商户");
        db.update("update store set status=? where id=? and tenant_id=?",status,id,tenant);revoke(tenant);audit("STATUS_"+status,"STORE",id);
    }
    public void deleteTenant(Long id) {
        PlatformContext.requireAdminId();lockTenant(id);lockTenantStores(id);ensureSettled(id,null);
        db.update("update store set status=0,deleted_at=CURRENT_TIMESTAMP where tenant_id=? and deleted_at is null",id);
        db.update("update tenant set status=0,deleted_at=CURRENT_TIMESTAMP where id=?",id);revoke(id);audit("DELETE","TENANT",id);
    }
    public void deleteStore(Long id) {
        PlatformContext.requireAdminId();Long tenant=tenantForStore(id);lockTenant(tenant);lockStore(tenant,id);ensureSettled(tenant,id);
        db.update("update store set status=0,deleted_at=CURRENT_TIMESTAMP where id=? and tenant_id=?",id,tenant);revoke(tenant);audit("DELETE","STORE",id);
    }
    private void ensureSettled(Long tenant,Long store) {
        String filter="o.tenant_id=?";List<Object> args=new ArrayList<>();args.add(tenant);if(store!=null){filter+=" and o.store_id=?";args.add(store);}
        // Locking reads see orders committed while waiting for the store barrier, even under REPEATABLE READ.
        List<Long> unsettled=db.queryForList("select o.id from orders o where "+filter+" and (o.status not in (5,6) or o.pay_status in (3,4) or (o.status=6 and o.pay_status=1) or (o.status=6 and o.pay_status=0 and (o.wechat_trade_state is null or o.wechat_trade_state<>'CLOSED')) or (o.refund_status is not null and o.refund_status<>'SUCCESS')) for update",Long.class,args.toArray());
        List<Long> refunds=db.queryForList("select o.id from orders o join refund_request r on r.order_id=o.id where "+filter+" and r.status<>'SUCCESS' for update",Long.class,args.toArray());
        if(!unsettled.isEmpty()||!refunds.isEmpty())throw bad("存在未完成订单或未结清退款，不能删除");
    }
    private Map<String,Object> lockTenant(Long id) {
        List<Map<String,Object>> rows=db.queryForList("select id,status from tenant where id=? and deleted_at is null for update",id);
        if(rows.isEmpty())throw bad("商户不存在或已删除");return rows.get(0);
    }
    private Long tenantForStore(Long id) {
        List<Long> rows=db.queryForList("select tenant_id from store where id=? and deleted_at is null",Long.class,id);
        if(rows.isEmpty())throw bad("门店不存在或已删除");return rows.get(0);
    }
    private void lockStore(Long tenant,Long id) {
        if(db.queryForList("select id from store where tenant_id=? and id=? and deleted_at is null for update",Long.class,tenant,id).isEmpty())throw bad("门店不存在或已删除");
    }
    private void lockTenantStores(Long id) { db.queryForList("select id from store where tenant_id=? order by id for update",Long.class,id); }
    private void revoke(Long id) { db.update("delete from admin_session where employee_id in(select id from employee where tenant_id=?)",id); }
    private Long insertStore(Long tenant,Map<String,Object> form) {
        Long id=insert("insert into store(tenant_id,code,name,status) values(?,?,?,1)",tenant,code(form),text(form,"name",100));
        db.update("insert into store_settings(tenant_id,store_id,address,latitude,longitude) values(?,?,?,?,?)",tenant,id,address(form),coordinate(form,"latitude",90),coordinate(form,"longitude",180));
        if("mock".equalsIgnoreCase(environment.getProperty("sky.payment.channel","wechat"))) {
            String account="mock-"+tenant+"-"+id;
            db.update("insert into payment_account(id,tenant_id,store_id,mchid,appid) values(?,?,?,?,?)",account,tenant,id,"MOCK-"+tenant+"-"+id,"MOCK-APP-"+tenant+"-"+id);
            db.update("insert into store_payment_account(tenant_id,store_id,payment_account_id) values(?,?,?)",tenant,id,account);
        }
        return id;
    }
    private void saveSettings(Long tenant,Long id,Map<String,Object> form) { db.update("update store_settings set version=version+1,address=?,latitude=?,longitude=? where tenant_id=? and store_id=?",address(form),coordinate(form,"latitude",90),coordinate(form,"longitude",180),tenant,id); }
    private void grant(Long id,String role,String permission) { db.update("insert into role_permission(tenant_id,role_code,permission_code) values(?,?,?)",id,role,permission); }
    private void audit(String action,String type,Long id) { db.update("insert into platform_audit(admin_id,action,target_type,target_id,created_at) values(?,?,?,?,CURRENT_TIMESTAMP)",PlatformContext.requireAdminId(),action,type,id); }
    private Long insert(String sql,Object... args) {
        GeneratedKeyHolder keys=new GeneratedKeyHolder();db.update(c->{PreparedStatement statement=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);for(int i=0;i<args.length;i++)statement.setObject(i+1,args[i]);return statement;},keys);return keys.getKey().longValue();
    }
    private void validateStore(Map<String,Object> f) {code(f);text(f,"name",100);address(f);BigDecimal lat=coordinate(f,"latitude",90),lng=coordinate(f,"longitude",180);if((lat==null)!=(lng==null))throw bad("经纬度必须同时填写");}
    private static String text(Map<String,Object> f,String key,int max) {Object value=f.get(key);if(!(value instanceof String))throw bad("请填写"+key);String s=((String)value).trim();if(s.isEmpty()||s.length()>max)throw bad(key+"长度不合法");return s;}
    private static String code(Map<String,Object> f) {String s=text(f,"code",64);if(!s.matches("[A-Za-z0-9_-]+"))throw bad("编码只支持字母、数字、下划线和连字符");return s;}
    private static String address(Map<String,Object> f) {Object value=f.get("address");if(value==null)return "";if(!(value instanceof String)||((String)value).length()>255)throw bad("地址长度不合法");return ((String)value).trim();}
    private static BigDecimal coordinate(Map<String,Object> f,String key,int max) {Object value=f.get(key);if(value==null||"".equals(value))return null;try{BigDecimal n=new BigDecimal(value.toString());if(n.abs().compareTo(BigDecimal.valueOf(max))>0||n.scale()>6)throw bad("经纬度范围或精度不合法");return n;}catch(NumberFormatException e){throw bad("经纬度格式不正确");}}
    private static Long number(Object value) {try {long n=Long.parseLong(String.valueOf(value));if(n<1)throw bad("编号不正确");return n;}catch(NumberFormatException e){throw bad("编号不正确");}}
    private static void checkStatus(Integer status) {if(status==null||(status!=0&&status!=1))throw bad("状态只能为0或1");}
    private static void checkPage(int page,int size) {if(page<1||size<1||size>100)throw bad("分页参数不合法");}
    private static String pattern(String keyword) {if(keyword!=null&&keyword.length()>100)throw bad("搜索词过长");return "%"+(keyword==null?"":keyword.trim())+"%";}
    private static BaseException bad(String message) {return new BaseException(message);}
}




