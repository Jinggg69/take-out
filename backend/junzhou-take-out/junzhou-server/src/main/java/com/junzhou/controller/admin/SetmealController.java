package com.junzhou.controller.admin;

import com.junzhou.dto.SetmealDTO;
import com.junzhou.dto.SetmealPageQueryDTO;
import com.junzhou.result.PageResult;
import com.junzhou.result.Result;
import com.junzhou.cache.ScopedCache;
import com.junzhou.context.RequestScope;
import com.junzhou.service.SetmealService;
import com.junzhou.vo.SetmealVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/setmeal")
@Api(tags = "套餐相关接口")
@Slf4j
public class SetmealController {
    @Autowired
    private SetmealService setmealService;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 新增套餐
     * @param setmealDTO
     * @return
     */
    @PostMapping
    @ApiOperation("新增套餐")
    public Result save(@RequestBody SetmealDTO setmealDTO) {
        log.info("新增套餐：{}", setmealDTO);
        RequestScope.requireStoreId();
        setmealService.saveWithDish(setmealDTO);
        ScopedCache.evictSetmeals(redisTemplate);
        return Result.success();
    }

    /**
     * 套餐分页查询
     * @param setmealPageQueryDTO
     * @return
     */
    @GetMapping("/page")
    @ApiOperation("套餐分页查询")
    public Result<PageResult> page(SetmealPageQueryDTO setmealPageQueryDTO) {
        log.info("套餐分页查询：{}", setmealPageQueryDTO);
        PageResult pageResult = setmealService.pageQuery(setmealPageQueryDTO);
        return Result.success(pageResult);
    }

    /**
     * 批量删除套餐
     * @param ids
     * @return
     */
    @DeleteMapping
    @ApiOperation("批量删除套餐")
    public Result delete(@RequestParam("ids") List<Long> ids) {
        log.info("批量删除套餐：{}", ids);
        RequestScope.requireStoreId();
        setmealService.deleteBatch(ids);
        ScopedCache.evictSetmeals(redisTemplate);
        return Result.success();
    }

    /**
     * 根据id查询套餐和对应的菜品
     * @param id
     * @return
     */
    @GetMapping("/{id}")
    @ApiOperation("根据id查询套餐")
    public Result<SetmealVO> getById(@PathVariable Long id) {
        log.info("根据id查询套餐：{}", id);
        SetmealVO setmealVO = setmealService.getByIdWithDish(id);
        return Result.success(setmealVO);
    }

    /**
     * 修改套餐
     * @param setmealDTO
     * @return
     */
    @PutMapping
    @ApiOperation("修改套餐")
    public Result update(@RequestBody SetmealDTO setmealDTO) {
        log.info("修改套餐：{}", setmealDTO);
        RequestScope.requireStoreId();
        setmealService.updateWithDish(setmealDTO);
        ScopedCache.evictSetmeals(redisTemplate);
        return Result.success();
    }

    /**
     * 套餐起售、停售
     * @param status 1为起售，0为停售
     * @param id 套餐id
     * @return
     */
    @PostMapping("/status/{status}")
    @ApiOperation("套餐起售、停售")
    public Result startOrStop(@PathVariable Integer status, @RequestParam Long id) {
        log.info("套餐起售停售：status={}, id={}", status, id);
        RequestScope.requireStoreId();
        setmealService.startOrStop(status, id);
        ScopedCache.evictSetmeals(redisTemplate);
        return Result.success();
    }
}
