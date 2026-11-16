package com.junzhou.platform;

import com.junzhou.dto.EmployeeLoginDTO;
import com.junzhou.exception.LoginFailedException;
import com.junzhou.service.PasswordHashService;
import com.junzhou.vo.EmployeeLoginVO;
import lombok.Data;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.util.*;

@Service
public class PlatformAuthService {
 private final JdbcTemplate db;
 private final PasswordHashService passwords;
 private static final SecureRandom RANDOM=new SecureRandom();
 private static final long TTL=8*60*60*1000L;
 public PlatformAuthService(javax.sql.DataSource source, PasswordHashService passwords) {this.db=new JdbcTemplate(source);this.passwords=passwords;}
 /** Null means the username belongs to the employee domain; credential errors never return null. */
 @Transactional
 public EmployeeLoginVO loginIfPlatform(EmployeeLoginDTO input) {
  if(input==null || input.getUsername()==null || input.getPassword()==null)throw failure();
  List<Admin> rows=db.query("select id,username,password,name,status from platform_admin where lower(username)=lower(?) for update",(rs,n)->new Admin(rs.getLong("id"),rs.getString("username"),rs.getString("password"),rs.getString("name"),rs.getInt("status")), input.getUsername());
  if(rows.isEmpty()) {if(PlatformUsernames.reserved(input.getUsername()))throw failure();return null;}
  Admin admin=rows.get(0);
  if(admin.status!=1 || !passwords.matches(input.getPassword(),admin.password))throw failure();
  byte[] secret=new byte[32]; RANDOM.nextBytes(secret);
  String token="platform_"+Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
  db.update("insert into platform_session(id,admin_id,expires_at) values(?,?,?)",digest(token),admin.id,new Timestamp(System.currentTimeMillis()+TTL));
  return view(admin,token);
 }
 public Long authenticate(String token) {
  if(token==null || !token.matches("platform_[A-Za-z0-9_-]{43}"))return null;
  List<Long> ids=db.query("select a.id from platform_session s join platform_admin a on a.id=s.admin_id where s.id=? and s.expires_at>current_timestamp and a.status=1",(rs,n)->rs.getLong(1),digest(token));
  return ids.size()==1 ? ids.get(0):null;
 }
 public EmployeeLoginVO me() {return view(current(false),null);}
 @Transactional public void logout(String token) {db.update("delete from platform_session where id=? and admin_id=?",digest(token),PlatformContext.requireAdminId());}
 @Transactional public void changePassword(PasswordChange input) {
  Admin admin=current(true);
  if(input==null || !passwords.matches(input.oldPassword,admin.password))throw new LoginFailedException("原密码不正确");
  if(input.newPassword==null || input.newPassword.length()<8 || input.newPassword.length()>128)throw new LoginFailedException("新密码长度须为8至128位");
  db.update("update platform_admin set password=? where id=?",passwords.encode(input.newPassword),admin.id);
  db.update("delete from platform_session where admin_id=?",admin.id);
 }
 private Admin current(boolean lock) {
  List<Admin> rows=db.query("select id,username,password,name,status from platform_admin where id=? and status=1"+(lock?" for update":""),(rs,n)->new Admin(rs.getLong("id"),rs.getString("username"),rs.getString("password"),rs.getString("name"),rs.getInt("status")),PlatformContext.requireAdminId());
  if(rows.isEmpty())throw new SecurityException("平台账号不可用");return rows.get(0);
 }
 private EmployeeLoginVO view(Admin a,String token) {return EmployeeLoginVO.builder().id(a.id).userName(a.username).name(a.name).token(token).accountType("PLATFORM").build();}
 private LoginFailedException failure(){return new LoginFailedException("账号或密码错误，或账号已停用");}
 static String digest(String token) {try {byte[] bytes=MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format("%02x",b&255));return out.toString();}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
 private static class Admin {final Long id;final String username,password,name;final int status;Admin(Long id,String u,String p,String n,int s){this.id=id;username=u;password=p;name=n;status=s;}}
 @Data public static class PasswordChange {private String oldPassword; private String newPassword; @Override public String toString(){return "PasswordChange[REDACTED]";}}
}
