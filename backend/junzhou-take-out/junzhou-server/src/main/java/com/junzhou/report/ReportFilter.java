package com.junzhou.report;
import lombok.Value;
import java.time.LocalDateTime;
import java.util.List;
/** Validated immutable query shared by charts and export; created by ReportService. */
@Value public class ReportFilter { Long tenant; List<Long> stores; LocalDateTime from; LocalDateTime until; }
