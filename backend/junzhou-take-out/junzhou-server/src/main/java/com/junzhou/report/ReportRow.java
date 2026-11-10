package com.junzhou.report;
import lombok.Data;
import java.math.BigDecimal;
/** Aggregate or reconciliation projection; no customer personal information. */
@Data public class ReportRow {
 private String day, name, number, fulfillmentType, event;
 private Long id, storeId, dishId, setmealId;
 private BigDecimal received, refunded, amount;
 private long placed, paid, completed, cancelled, quantity, newUsers, totalUsers;
}
