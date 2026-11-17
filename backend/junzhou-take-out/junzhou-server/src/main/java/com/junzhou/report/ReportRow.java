package com.junzhou.report;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
/** Aggregate or reconciliation projection; no customer personal information. Cached snapshots rely on JDK serialization. */
@Data public class ReportRow implements Serializable {
 private static final long serialVersionUID = 1L;
 private String day, name, number, fulfillmentType, event;
 private Long id, storeId, dishId, setmealId;
 private BigDecimal received, refunded, amount;
 private long placed, paid, completed, cancelled, quantity, newUsers, totalUsers;
}
