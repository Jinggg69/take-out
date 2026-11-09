package com.junzhou.test.support;

import org.springframework.jdbc.core.JdbcTemplate;

public final class StoreSettingsTestSchema {
    private StoreSettingsTestSchema() { }
    public static void create(JdbcTemplate jdbc) {
        jdbc.execute("alter table store add name varchar(100) default '测试门店'");
        jdbc.execute("create table store_settings(tenant_id bigint,store_id bigint,version bigint default 0,address varchar(255) default '',"
                + "latitude decimal(9,6),longitude decimal(9,6),opens_at varchar(5) default '00:00',closes_at varchar(5) default '00:00',"
                + "delivery_radius_meters int default 5000,minimum_order_amount decimal(10,2) default 0,pack_fee_per_item decimal(10,2) default 1,"
                + "delivery_fee decimal(10,2) default 6,free_delivery_threshold decimal(10,2),primary key(tenant_id,store_id))");
    }
}
