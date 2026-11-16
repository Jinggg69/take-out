package com.junzhou.platform;

import com.junzhou.service.impl.PasswordHashServiceImpl;
import java.io.Console;
import java.sql.*;
import java.security.SecureRandom;
import java.util.*;

/** Explicit local maintenance command; never registered as a web endpoint or startup runner. */
public final class PlatformAdminBootstrap {
 private PlatformAdminBootstrap() { }
 public static void main(String[] args) throws Exception {
  Console console=System.console();
  if(console==null)throw new IllegalStateException("Run from an interactive terminal; passwords cannot be passed as command-line arguments");
  if(args.length!=0)throw new IllegalArgumentException("Use PLATFORM_DB_URL / PLATFORM_DB_USER; no command-line credentials");
  String url=System.getenv("PLATFORM_DB_URL"),user=System.getenv("PLATFORM_DB_USER");
  if(url==null || !url.startsWith("jdbc:mysql:") || user==null)throw new IllegalArgumentException("Set PLATFORM_DB_URL (jdbc:mysql:...) and PLATFORM_DB_USER");
  String username=console.readLine("Platform username [platform_admin]: ");
  if(username==null || username.isEmpty())username="platform_admin";
  username=username.toLowerCase(Locale.ROOT);
  if(!username.matches("platform_[a-z0-9_]{1,55}"))throw new IllegalArgumentException("Username must use platform_ prefix and lowercase letters/numbers/underscores");
  String name=console.readLine("Display name: ");
  if(name==null || name.trim().isEmpty() || name.length()>64)throw new IllegalArgumentException("Display name is required (max 64)");
  char[] databasePassword=console.readPassword("Database password: ");
  char[] password=console.readPassword("Initial platform password (blank generates a random credential): ");
  boolean generated=password!=null && password.length==0;
  if(generated){byte[] random=new byte[24];new SecureRandom().nextBytes(random);password=Base64.getUrlEncoder().withoutPadding().encodeToString(random).toCharArray();}
  if(password==null || password.length<12 || password.length>128)throw new IllegalArgumentException("Use a password between 12 and 128 characters");
  try(Connection connection=DriverManager.getConnection(url,user,new String(databasePassword))){
   connection.setAutoCommit(false);
   try {
    try(PreparedStatement collision=connection.prepareStatement("select id from employee where lower(username)=lower(?)")){
     collision.setString(1,username);try(ResultSet rows=collision.executeQuery()){if(rows.next())throw new IllegalStateException("Username already belongs to an employee");}
    }
    try(PreparedStatement insert=connection.prepareStatement("insert into platform_admin(username,password,name,status) values(?,?,?,1)")){
     insert.setString(1,username);insert.setString(2,new PasswordHashServiceImpl().encode(new String(password)));insert.setString(3,name);insert.executeUpdate();
    }
    connection.commit();console.printf("Platform account created: %s%n",username);
    if(generated)console.printf("Initial credential (store securely): %s%n",new String(password));
   }catch(Exception failure){connection.rollback();throw failure;}
  }finally{if(databasePassword!=null)Arrays.fill(databasePassword,'\0');if(password!=null)Arrays.fill(password,'\0');}
 }
}
