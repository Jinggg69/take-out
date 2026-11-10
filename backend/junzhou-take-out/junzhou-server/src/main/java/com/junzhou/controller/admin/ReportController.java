package com.junzhou.controller.admin;
import com.junzhou.service.ReportService;
import com.junzhou.report.ReportFilter;
import com.junzhou.result.Result;
import com.junzhou.security.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import java.util.*;
@RestController @RequestMapping("/admin/report")
public class ReportController {
 private final ReportService reports;
 public ReportController(ReportService reports){this.reports=reports;}
 @GetMapping("/turnoverStatistics") @RequireActions(PermissionAction.REPORT_READ)
 public Result<Map<String,Object>> turnover(@RequestParam String begin,@RequestParam String end,@RequestParam(required=false) List<Long> storeIds){return Result.success(reports.turnover(reports.filter(begin,end,storeIds,false)));}
 @GetMapping("/ordersStatistics") @RequireActions(PermissionAction.REPORT_READ)
 public Result<Map<String,Object>> orders(@RequestParam String begin,@RequestParam String end,@RequestParam(required=false) List<Long> storeIds){return Result.success(reports.orders(reports.filter(begin,end,storeIds,false)));}
 @GetMapping("/userStatistics") @RequireActions(PermissionAction.REPORT_READ)
 public Result<Map<String,Object>> users(@RequestParam String begin,@RequestParam String end,@RequestParam(required=false) List<Long> storeIds){return Result.success(reports.users(reports.filter(begin,end,storeIds,false)));}
 @GetMapping("/top10") @RequireActions(PermissionAction.REPORT_READ)
 public Result<Map<String,Object>> top10(@RequestParam String begin,@RequestParam String end,@RequestParam(required=false) List<Long> storeIds){return Result.success(reports.top10(reports.filter(begin,end,storeIds,false)));}
 @GetMapping("/export") @RequireActions({PermissionAction.REPORT_READ,PermissionAction.REPORT_EXPORT})
 public ResponseEntity<byte[]> export(@RequestParam(required=false) String begin,@RequestParam(required=false) String end,@RequestParam(required=false) List<Long> storeIds){
  ReportFilter f=reports.filter(begin,end,storeIds,true);
  return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=report.xlsx").contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).body(reports.export(f));
 }
}
