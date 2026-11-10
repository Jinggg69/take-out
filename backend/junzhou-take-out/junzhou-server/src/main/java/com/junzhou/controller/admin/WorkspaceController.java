package com.junzhou.controller.admin;
import com.junzhou.service.ReportService;
import com.junzhou.result.Result;
import com.junzhou.security.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/admin/workspace")
public class WorkspaceController {
 private final ReportService reports;
 public WorkspaceController(ReportService reports){this.reports=reports;}
 @GetMapping("/businessData") @RequireActions(PermissionAction.WORKSPACE_READ)
 public Result<Map<String,Object>> businessData(){return Result.success(reports.business());}
 @GetMapping("/overviewOrders") @RequireActions(PermissionAction.WORKSPACE_READ)
 public Result<Map<String,Object>> overviewOrders(){return Result.success(reports.overview());}
 @GetMapping("/overviewDishes") @RequireActions(PermissionAction.WORKSPACE_READ)
 public Result<Map<String,Object>> overviewDishes(){return Result.success(reports.menu("dish"));}
 @GetMapping("/overviewSetmeals") @RequireActions(PermissionAction.WORKSPACE_READ)
 public Result<Map<String,Object>> overviewSetmeals(){return Result.success(reports.menu("setmeal"));}
}
