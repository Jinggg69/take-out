package com.junzhou.controller.user;

import com.junzhou.entity.DiningTable;
import com.junzhou.result.Result;
import com.junzhou.service.DiningService;
import com.junzhou.annotation.MiniappEntry;
import com.junzhou.vo.DiningEntryVO;
import com.junzhou.exception.MiniappApiException;
import com.junzhou.exception.OrderBusinessException;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController("userDiningController") @RequestMapping("/user/dining")
public class DiningController {
    private final DiningService service;
    public DiningController(DiningService service){this.service=service;}
    @MiniappEntry(MiniappEntry.Scope.IDENTITY)
    @PostMapping("/resolve") public Result<DiningEntryVO> resolve(@RequestBody Map<String,String> body){
        String token=body.get("token");
        if(token==null || !token.matches("[0-9a-f]{32}")) throw new MiniappApiException(400,"桌码格式无效");
        try {return Result.success(DiningEntryVO.from(service.resolve(token),false));}
        catch(OrderBusinessException ex){throw new MiniappApiException(404,"桌码或桌次已失效，请重新扫码确认");}
    }
    @PostMapping("/join") public Result<DiningEntryVO> join(@RequestBody Map<String,String> body){return Result.success(DiningEntryVO.from(service.join(body.get("token")),true));}
}
