package com.junzhou.controller.user;

import com.junzhou.annotation.MiniappEntry;
import com.junzhou.context.RequestScope;
import com.junzhou.entity.User;
import com.junzhou.exception.MiniappApiException;
import com.junzhou.mapper.UserMapper;
import com.junzhou.result.Result;
import org.springframework.web.bind.annotation.*;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class UserProfileController {
    private final UserMapper users;
    public UserProfileController(UserMapper users){ this.users=users; }
    @MiniappEntry(MiniappEntry.Scope.IDENTITY)
    @GetMapping("/user/me")
    public Result<Map<String,Object>> profile(){
        Long id=RequestScope.requireAuthenticatedUserId();
        User user=users.getProfileById(id);
        if(user==null) throw new MiniappApiException(401,"请重新登录");
        Map<String,Object> data=new LinkedHashMap<>();
        data.put("userId",id.toString()); data.put("nickname",user.getName()); data.put("avatarUrl",user.getAvatar());
        return Result.success(data);
    }
}
