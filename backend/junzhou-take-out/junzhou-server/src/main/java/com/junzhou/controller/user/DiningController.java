package com.junzhou.controller.user;

import com.junzhou.entity.DiningTable;
import com.junzhou.result.Result;
import com.junzhou.service.DiningService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController("userDiningController") @RequestMapping("/user/dining")
public class DiningController {
    private final DiningService service;
    public DiningController(DiningService service){this.service=service;}
    @PostMapping("/resolve") public Result<DiningTable> resolve(@RequestBody Map<String,String> body){return Result.success(service.resolve(body.get("token")));}
    @PostMapping("/join") public Result<DiningTable> join(@RequestBody Map<String,String> body){return Result.success(service.join(body.get("token")));}
}
