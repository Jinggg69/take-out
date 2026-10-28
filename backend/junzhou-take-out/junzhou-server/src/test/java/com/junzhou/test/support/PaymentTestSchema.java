package com.junzhou.test.support;

import org.springframework.jdbc.core.JdbcTemplate;

/** Test DDL for the additive payment migration. */
public final class PaymentTestSchema {
    private PaymentTestSchema() {}
    public static void upgrade(JdbcTemplate jdbc) {
        jdbc.execute("alter table orders add (dispatch_time timestamp, wechat_transaction_id varchar(64), "
                + "refund_status varchar(16), payment_check_time timestamp, wechat_trade_state varchar(32), "
                + "client_request_id varchar(64), submit_fingerprint varchar(64))");
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
