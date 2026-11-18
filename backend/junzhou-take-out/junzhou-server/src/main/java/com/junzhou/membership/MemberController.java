package com.junzhou.membership;
import com.junzhou.result.Result;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
@RequestMapping("/user/member")
public class MemberController {
 private final MembershipService s;
 public MemberController(MembershipService s){this.s=s;}
 @GetMapping("/me") public Result<?> me(){return Result.success(s.profile());}
 @PostMapping("/join") public Result<?> join(@RequestBody Map<String,Object>b){return Result.success(s.join(b));}
 @PutMapping("/profile") public Result<?> profile(@RequestBody Map<String,Object>b){s.updateOwn(b);return Result.success(s.profile());}
 @GetMapping({"/points","/wallet","/orders","/coupons","/recharges"}) public Result<?> history(javax.servlet.http.HttpServletRequest req,@RequestParam Map<String,String>q){return Result.success(s.ownHistory(req.getRequestURI().substring(req.getRequestURI().lastIndexOf('/')+1),q));}
 @GetMapping("/recharge-plans") public Result<?> plans(){com.junzhou.context.RequestScope.requireUserId();return Result.success(s.config("recharge-plans",true));}
 @GetMapping("/coupon-center") public Result<?> center(@RequestParam Map<String,String>q){return Result.success(s.center(q));}
 @PostMapping("/coupons/{id}/claim") public Result<?> claim(@PathVariable long id,@RequestBody Map<String,Object>b){return Result.success(s.claim(id,b,false));}
 @PostMapping("/coupons/{id}/exchange") public Result<?> exchange(@PathVariable long id,@RequestBody Map<String,Object>b){return Result.success(s.claim(id,b,true));}
 @PostMapping("/recharges") public Result<?> recharge(@RequestBody Map<String,Object>b){return Result.success(s.recharge(b));}
 @PostMapping("/recharges/{id}/mock") public Result<?> mock(@PathVariable long id,@RequestBody Map<String,Object>b){return Result.success(s.mockRecharge(id,b));}
 @PostMapping("/recharges/{id}/refund") public Result<?> refund(@PathVariable long id,@RequestBody Map<String,Object>b){return Result.success(s.refundRecharge(id,b,false));}
}
