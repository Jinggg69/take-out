package com.junzhou.mapper;

import com.junzhou.entity.Dish;
import com.junzhou.entity.Setmeal;
import com.junzhou.entity.StoreMenuItem;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface StoreMenuMapper {
    Long lockStore();
    List<StoreMenuItem> dishes();
    List<StoreMenuItem> setmeals();
    int saveDish(StoreMenuItem item);
    int saveSetmeal(StoreMenuItem item);
    List<Dish> publicDishes(Long categoryId);
    List<Setmeal> publicSetmeals(Long categoryId);
}
