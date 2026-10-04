package com.sky.controller.user;

import com.sky.constant.StatusConstant;
import com.sky.entity.Dish;
import com.sky.result.Result;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@RestController("userDishController")
@RequestMapping("/user/dish")
@Slf4j
@Api(tags = "C端-菜品浏览接口")
public class DishController {
    @Autowired
    private DishService dishService;
    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 根据分类id查询菜品
     *
     * @param categoryId
     * @return
     */
    @GetMapping("/list")
    @ApiOperation("根据分类id查询菜品")
    public Result<List<DishVO>> list(Long categoryId) {
        //设置key的规则dish_+ 分类id
        String key = "dish_" + categoryId;
        //查询redis中是否有菜品数据
        List<DishVO> list = (List<DishVO>) redisTemplate.opsForValue().get(key);
        //有则直接返回
         if (list != null && list.size() > 0){
             return Result.success(list);
         }
        //没有则查询数据库，并添加到redis中

        Dish dish = new Dish();
        dish.setCategoryId(categoryId);
        dish.setStatus(StatusConstant.ENABLE);//查询起售中的菜品

        list = dishService.listWithFlavor(dish);
        //random作ttl的时间偏移，防止缓存击穿
        int random = new Random().nextInt(600);
        //设置缓存过期时间10分钟+随机数
        redisTemplate.opsForValue().set(key, list, 600 + random, TimeUnit.SECONDS);

        return Result.success(list);
    }

}
