package com.junzhou.mapper;

import com.junzhou.entity.RefundRequest;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface RefundRequestMapper {
    RefundRequest findByOrderId(Long orderId);
    RefundRequest getByOrderIdForUpdate(Long orderId);
    RefundRequest getByNumber(String number);
    RefundRequest getByNumberForUpdate(String number);
    List<String> due(LocalDateTime now);
    int insert(RefundRequest request);
    int update(RefundRequest request);
}
