package com.junzhou.membership;
import com.junzhou.result.Result;
import com.junzhou.security.PermissionAction;
import com.junzhou.security.RequireActions;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
@RequestMapping("/admin/member")
public class AdminMemberController {
 private final MembershipService s;
 public AdminMemberController(MembershipService s){this.s=s;}
 @GetMapping("/page") @RequireActions(PermissionAction.MEMBER_READ) public Result<?> page(@RequestParam Map<String,String>q){return Result.success(s.pageMembers(q));}
 @GetMapping("/{id:\\d+}") @RequireActions(PermissionAction.MEMBER_READ) public Result<?> detail(@PathVariable long id){return Result.success(s.detail(id));}
 @PostMapping({"","/"}) @RequireActions(PermissionAction.MEMBER_CREATE) public Result<?> create(@RequestBody Map<String,Object>b){return Result.success(s.createAdmin(b));}
 @PutMapping("/{id:\\d+}") @RequireActions(PermissionAction.MEMBER_UPDATE) public Result<?> update(@PathVariable long id,@RequestBody Map<String,Object>b){s.updateAdmin(id,b);return Result.success();}
 @PostMapping("/{id}/status") @RequireActions(PermissionAction.MEMBER_STATUS) public Result<?> status(@PathVariable long id,@RequestBody Map<String,Object>b){s.status(id,b);return Result.success();}
 @PostMapping("/{id}/points") @RequireActions(PermissionAction.MEMBER_POINT) public Result<?> points(@PathVariable long id,@RequestBody Map<String,Object>b){s.adjust(id,b,true);return Result.success();}
 @PostMapping("/{id}/balance") @RequireActions(PermissionAction.MEMBER_BALANCE) public Result<?> balance(@PathVariable long id,@RequestBody Map<String,Object>b){s.adjust(id,b,false);return Result.success();}
 @GetMapping({"/levels","/tags","/recharge-plans","/coupons"}) @RequireActions({}) public Result<?> config(javax.servlet.http.HttpServletRequest req){String k=req.getRequestURI().substring(req.getRequestURI().lastIndexOf('/')+1);return Result.success(s.config(k,false));}
 @PostMapping("/{kind:levels|tags|recharge-plans|coupons}") @RequireActions({}) public Result<?> createConfig(@PathVariable String kind,@RequestBody Map<String,Object>b){return Result.success(s.saveConfig(kind,null,b));}
 @PutMapping("/{kind:levels|tags|recharge-plans|coupons}/{id}") @RequireActions({}) public Result<?> updateConfig(@PathVariable String kind,@PathVariable long id,@RequestBody Map<String,Object>b){return Result.success(s.saveConfig(kind,id,b));}
 @GetMapping("/settings") @RequireActions({}) public Result<?> settings(){s.admin(PermissionAction.MEMBER_READ,false);return Result.success(s.settings());}
 @PutMapping("/settings") @RequireActions({}) public Result<?> settings(@RequestBody Map<String,Object>b){s.saveSettings(b);return Result.success();}
 @GetMapping({"/wallet","/recharges"}) @RequireActions(PermissionAction.MEMBER_READ) public Result<?> history(javax.servlet.http.HttpServletRequest req,@RequestParam Map<String,String>q){return Result.success(s.adminHistory(req.getRequestURI().substring(req.getRequestURI().lastIndexOf('/')+1),q));}
 @PostMapping("/recharges/{id}/refund") @RequireActions(PermissionAction.MEMBER_REFUND) public Result<?> refund(@PathVariable long id,@RequestBody Map<String,Object>b){return Result.success(s.refundRecharge(id,b,true));}
 @PostMapping("/coupons/{id}/issue") @RequireActions(PermissionAction.COUPON_ISSUE) public Result<?> issue(@PathVariable long id,@RequestBody Map<String,Object>b){s.issue(id,b);return Result.success();}
}
