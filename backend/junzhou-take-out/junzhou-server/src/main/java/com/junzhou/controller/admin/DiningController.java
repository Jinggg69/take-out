package com.junzhou.controller.admin;

import com.junzhou.entity.DiningTable;
import com.junzhou.result.Result;
import com.junzhou.security.*;
import com.junzhou.service.DiningService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController("adminDiningController") @RequestMapping("/admin/dining")
public class DiningController {
    private final DiningService service;
    public DiningController(DiningService service){this.service=service;}
    @GetMapping @RequireActions({PermissionAction.DINING_READ})
    public Result<List<DiningTable>> tables(){return Result.success(service.tables());}
    @PostMapping @RequireActions({PermissionAction.DINING_MANAGE})
    public Result<DiningTable> save(@RequestBody DiningTable table){return Result.success(service.save(table));}
    @GetMapping("/{id}/code") @RequireActions({PermissionAction.DINING_MANAGE})
    public Result<String> code(@PathVariable Long id){return Result.success(service.code(id,false));}
    @PutMapping("/{id}/code") @RequireActions({PermissionAction.DINING_MANAGE})
    public Result<String> rotate(@PathVariable Long id){return Result.success(service.code(id,true));}
    @PutMapping("/{id}/close") @RequireActions({PermissionAction.DINING_CLOSE})
    public Result<Void> close(@PathVariable Long id){service.close(id);return Result.success();}
}
