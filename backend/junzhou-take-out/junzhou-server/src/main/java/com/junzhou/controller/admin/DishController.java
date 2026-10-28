package com.junzhou.controller.admin;

import com.junzhou.dto.DishDTO;
import com.junzhou.dto.DishPageQueryDTO;
import com.junzhou.entity.Dish;
import com.junzhou.result.PageResult;
import com.junzhou.result.Result;
import com.junzhou.service.DishService;
import com.junzhou.vo.DishVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/dish")
@Api(tags = "菜品相关接口")
@Slf4j
public class DishController {
    @Autowired
    private DishService dishService;
    @Autowired
    private RedisTemplate redisTemplate;
    /**
     * 新增菜品
     * @param dishDTO
     * @return
     */
    @PostMapping
    @ApiOperation("新增菜品")
    public Result save(@RequestBody DishDTO dishDTO) {
        log.info("新增菜品：{}",dishDTO);
        dishService.saveWithFlavor(dishDTO);

        //清理缓存数据
        String key = "dish_" + dishDTO.getCategoryId();
        redisTemplate.delete(key);
        return Result.success();
    }

    /**
     * 菜品分页查询
     * @param dishPageQueryDTO
     * @return
     */
    @GetMapping("/page")
    @ApiOperation("菜品分页查询")
    public Result<PageResult> page(DishPageQueryDTO dishPageQueryDTO) {
        log.info("菜品分页查询：{}", dishPageQueryDTO);
        PageResult pageResult = dishService.pageQuery(dishPageQueryDTO);
        return Result.success(pageResult);
    }

    /**
     * 批量删除菜品
     * @param ids
     * @return
     */
    @DeleteMapping
    @ApiOperation("批量删除菜品")
    public Result delete(@RequestParam("ids") List<Long> ids) {
        log.info("批量删除菜品：{}", ids);
        dishService.deleteBatch(ids);

        //清理所有的菜品缓存数据
        clearCache("dish_*");
        return Result.success();
    }

    /**
     * 根据id查询菜品和对应的口味
     * @param id
     * @return
     */
    @GetMapping("/{id}")
    @ApiOperation("根据id查询菜品")
    public Result<DishVO> getById(@PathVariable Long id) {
        log.info("根据id查询菜品：{}", id);
        DishVO dishVO = dishService.getByIdWithFlavor(id);
        return Result.success(dishVO);
    }

    /**
     * 根据分类id查询在售菜品，供套餐选择菜品使用
     * @param categoryId
     * @return
     */
    @GetMapping("/list")
    @ApiOperation("根据分类id查询在售菜品")
    public Result<List<Dish>> list(@RequestParam Long categoryId) {
        log.info("根据分类id查询在售菜品：{}", categoryId);
        return Result.success(dishService.list(categoryId));
    }

    /**
     * 修改菜品
     * @param dishDTO
     * @return
     */
    @PutMapping()
    @ApiOperation("修改菜品")
    public Result update(@RequestBody DishDTO dishDTO) {
        log.info("修改菜品：{}", dishDTO);
        dishService.updateWithFlavor(dishDTO);

        //清理所有的菜品缓存数据
        clearCache("dish_*");
        return Result.success();
    }

    /**
     * 菜品起售、停售
     * @param status 1为起售，0为停售
     * @param id 菜品id
     */
    @PostMapping("/status/{status}")
    @ApiOperation("菜品起售、停售")
    public Result startOrStop(@PathVariable Integer status, @RequestParam Long id) {
        log.info("菜品起售停售：status={}, id={}", status, id);
        dishService.startOrStop(status, id);

        //清理所有的菜品缓存数据
        clearCache("dish_*");
        return Result.success();
    }
    /**
     * 清理缓存
     * @param pattern
     */
    private void clearCache(String pattern) {
        redisTemplate.delete(redisTemplate.keys(pattern));
    }
}
