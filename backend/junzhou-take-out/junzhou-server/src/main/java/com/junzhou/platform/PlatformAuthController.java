package com.junzhou.platform;
import com.junzhou.result.Result;
import com.junzhou.vo.EmployeeLoginVO;
import com.junzhou.properties.JwtProperties;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;
@RestController
@RequestMapping("/platform/auth")
public class PlatformAuthController {
 private final PlatformAuthService auth;private final JwtProperties jwt;
 public PlatformAuthController(PlatformAuthService auth,JwtProperties jwt){this.auth=auth;this.jwt=jwt;}
 @GetMapping("/me") public Result<EmployeeLoginVO> me(){return Result.success(auth.me());}
 @PostMapping("/logout") public Result<Void> logout(HttpServletRequest request){auth.logout(request.getHeader(jwt.getAdminTokenName()));return Result.success();}
 @PutMapping("/password") public Result<Void> password(@RequestBody PlatformAuthService.PasswordChange input){auth.changePassword(input);return Result.success();}
}
