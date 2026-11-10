package com.junzhou.service;
import com.junzhou.report.ReportFilter;
import java.util.*;
/** Shanghai calendar-day operational reporting. */
public interface ReportService {
 /** Validate inclusive date bounds and each store's current read/export authority; rejects absent identity or forbidden stores. */
 ReportFilter filter(String begin,String end,List<Long> stores,boolean export);
 /** Return daily confirmed receipts, confirmed refunds and net receipts for validated scope. */
 Map<String,Object> turnover(ReportFilter f);
 /** Return separate placed, paid, completed and cancelled event counts. */
 Map<String,Object> orders(ReportFilter f);
 /** Return scoped first-order customer cohorts, never global registered users. */
 Map<String,Object> users(ReportFilter f);
 /** Return top ten paid item snapshot quantities, net of full refunds confirmed by interval end. */
 Map<String,Object> top10(ReportFilter f);
 /** Return today's current-store financial summary in Asia/Shanghai. */
 Map<String,Object> business();
 /** Return today's placed-order status distribution for the current store. */
 Map<String,Object> overview();
 /** Return current store's saleable and discontinued menu counts; kind is dish or setmeal. */
 Map<String,Object> menu(String kind);
 /** Export same daily aggregates, item totals and event reconciliation; fails if over 50000 events. */
 byte[] export(ReportFilter f);
}
