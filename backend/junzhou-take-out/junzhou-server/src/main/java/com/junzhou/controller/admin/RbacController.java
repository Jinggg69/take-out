package com.junzhou.controller.admin;
import com.junzhou.security.*;
import com.junzhou.service.RbacService;
import com.junzhou.result.Result;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/admin/rbac")
public class RbacController {
 private final RbacService rbac;
 public RbacController(RbacService rbac){this.rbac=rbac;}
 @GetMapping("/context") @RequireActions({})
 public Result<java.util.Map<String,Object>> context(){return Result.success(rbac.context());}
 @GetMapping("/configuration") @RequireActions(PermissionAction.EMPLOYEE_MANAGE)
 public Result<java.util.Map<String,Object>> configuration(){return Result.success(rbac.configuration());}
 @PutMapping("/roles/{role}/permissions") @RequireActions(PermissionAction.EMPLOYEE_MANAGE)
 public Result<Void> permissions(@PathVariable String role,@RequestBody List<String> actions){rbac.replacePermissions(role,actions);return Result.success();}
 @PutMapping("/employees/{employee}/assignments") @RequireActions(PermissionAction.EMPLOYEE_MANAGE)
 public Result<Void> assignments(@PathVariable Long employee,@RequestBody List<RoleGrantInput> grants){rbac.replaceAssignments(employee,grants);return Result.success();}
}
