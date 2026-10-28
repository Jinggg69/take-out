package com.junzhou.mapper;

import com.junzhou.entity.RefundRequest;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface RefundRequestMapper {
    @Select("select * from refund_request where order_id=#{orderId}")
    RefundRequest findByOrderId(Long orderId);
    @Select("select * from refund_request where order_id=#{orderId} for update")
    RefundRequest getByOrderIdForUpdate(Long orderId);
    @Select("select * from refund_request where out_refund_no=#{number}")
    RefundRequest getByNumber(String number);
    @Select("select * from refund_request where out_refund_no=#{number} for update")
    RefundRequest getByNumberForUpdate(String number);
    @Select("select out_refund_no from refund_request where status in ('REQUESTED','PROCESSING','VERIFYING') "
            + "and next_attempt_at<=#{now} and (lease_until is null or lease_until<=#{now}) "
            + "order by next_attempt_at,out_refund_no limit 20")
    List<String> due(LocalDateTime now);
    @Insert("insert into refund_request(out_refund_no,order_id,out_trade_no,transaction_id,amount_fen,status,query_only," 
            + "attempts,next_attempt_at,created_at,updated_at) values(#{outRefundNo},#{orderId},#{outTradeNo},#{transactionId},"
            + "#{amountFen},#{status},#{queryOnly},0,#{nextAttemptAt},#{createdAt},#{updatedAt})")
    int insert(RefundRequest request);
    @Update("update refund_request set status=#{status},transaction_id=#{transactionId},provider_refund_id=#{providerRefundId},"
            + "attempts=#{attempts},next_attempt_at=#{nextAttemptAt},lease_until=#{leaseUntil},lease_token=#{leaseToken},"
            + "updated_at=#{updatedAt},last_error=#{lastError} where out_refund_no=#{outRefundNo}")
    int update(RefundRequest request);
}
