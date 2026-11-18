package com.junzhou.service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
/** Called inside platform provisioning transaction with the newly inserted tenant ID. */
@Service public class MembershipDefaults {
 private final JdbcTemplate db;
 public MembershipDefaults(JdbcTemplate db){this.db=db;}
 public void initialize(Long tenant){
  if(tenant==null || tenant<=0)throw new IllegalArgumentException("Invalid tenant");
  String[] names={"普通会员","银卡","金卡"};long[] thresholds={0,50000,200000};
  for(int i=0;i<names.length;i++)db.update("insert into member_level(tenant_id,name,threshold,discount_percent,status) values(?,?,?,100,1)",tenant,names[i],thresholds[i]);
 }
}
