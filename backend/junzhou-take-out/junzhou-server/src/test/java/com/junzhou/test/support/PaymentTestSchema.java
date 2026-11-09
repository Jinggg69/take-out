package com.junzhou.test.support;

import org.springframework.jdbc.core.JdbcTemplate;

/** Test DDL for the additive payment migration. */
public final class PaymentTestSchema {
    private PaymentTestSchema() {}
    public static void upgrade(JdbcTemplate jdbc) {
        jdbc.execute("create table tenant(id bigint primary key,status int)");
        jdbc.execute("create table store(id bigint primary key,tenant_id bigint,status int)");
        jdbc.execute("insert into tenant values(1,1)");
        jdbc.execute("insert into store values(1,1,1)");
        jdbc.execute("alter table orders add (tenant_id bigint default 1 not null, store_id bigint default 1 not null)");
        jdbc.execute("alter table orders add (dispatch_time timestamp, wechat_transaction_id varchar(64), "
                + "refund_status varchar(16), payment_check_time timestamp, wechat_trade_state varchar(32), "
                + "client_request_id varchar(64), submit_fingerprint varchar(64))");
        jdbc.execute("create table payment_account(id varchar(64) primary key,tenant_id bigint,store_id bigint,mchid varchar(64),appid varchar(64))");
        jdbc.execute("insert into payment_account values('legacy',1,1,'test-merchant','test-app')");
        jdbc.execute("create table store_payment_account(tenant_id bigint,store_id bigint,payment_account_id varchar(64),primary key(tenant_id,store_id))");
        jdbc.execute("insert into store_payment_account values(1,1,'legacy')");
        StoreSettingsTestSchema.create(jdbc);
        jdbc.execute("alter table orders add (store_snapshot text,pricing_snapshot text)");
        jdbc.execute("alter table orders modify address_book_id bigint null");
        jdbc.execute("alter table orders add (fulfillment_type varchar(16) default 'DELIVERY', fulfillment_status varchar(32) default 'AWAITING_PAYMENT', contact_name varchar(32), contact_phone varchar(11), table_id bigint, table_snapshot varchar(64), cart_fingerprint varchar(64))");
        jdbc.execute("create table store_dining_table(id bigint primary key,tenant_id bigint,store_id bigint,name varchar(64),enabled int)");
        jdbc.execute("alter table orders add payment_account_id varchar(64) default 'legacy'");
        jdbc.execute("create unique index uk_order_number on orders(number)");
        jdbc.execute("create unique index uk_order_transaction on orders(wechat_transaction_id)");
        jdbc.execute("create unique index uk_order_submission on orders(user_id,client_request_id)");
        jdbc.execute("create table refund_request (out_refund_no varchar(64) primary key, order_id bigint not null unique, "
                + "out_trade_no varchar(50) not null, transaction_id varchar(64), amount_fen bigint not null, "
                + "status varchar(16) not null, query_only boolean not null default false, provider_refund_id varchar(64) unique, attempts int not null default 0, "
                + "next_attempt_at timestamp not null, lease_until timestamp, lease_token varchar(36), "
                + "created_at timestamp not null, updated_at timestamp not null, last_error varchar(255))");
    }
}
