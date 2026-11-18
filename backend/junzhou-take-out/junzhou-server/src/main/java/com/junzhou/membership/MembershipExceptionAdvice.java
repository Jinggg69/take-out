package com.junzhou.membership;
import com.junzhou.result.Result;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice(assignableTypes={MemberController.class,AdminMemberController.class})
public class MembershipExceptionAdvice {
 @ExceptionHandler(IllegalArgumentException.class) public Result<?> invalid(IllegalArgumentException e){return Result.error(e.getMessage());}
 @ExceptionHandler(org.springframework.dao.DuplicateKeyException.class) public Result<?> concurrent(){return Result.error("请求正在处理或数据已存在，请刷新后重试");}
}
