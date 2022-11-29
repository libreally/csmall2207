package cn.tedu.mall.front.service.impl;

import cn.tedu.mall.front.service.IFrontCategoryService;
import cn.tedu.mall.pojo.front.entity.FrontCategoryEntity;
import cn.tedu.mall.pojo.front.vo.FrontCategoryTreeVO;
import cn.tedu.mall.product.service.front.IForFrontCategoryService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;


@Slf4j
@Service
public class FrontCategoryServiceImpl implements IFrontCategoryService {

    //调用product模块
    @DubboReference
    private IForFrontCategoryService DubboCategoryService;

    //操作redis对象
    @Autowired
    private RedisTemplate redisTemplate;
    //redis常量,用于redis中的key
    public static final String CATEGORY_TREE_KEY="category_tree";

    //返回三级分类树对象的方法
    @Override
    public FrontCategoryTreeVO categoryTree() {
        //先检查redis中是否已经存在了三级分类树对象
        if (redisTemplate.hasKey(CATEGORY_TREE_KEY)){
            //redis中如已经有了这个key直接获取
            FrontCategoryTreeVO<FrontCategoryEntity> treeVO = (FrontCategoryTreeVO<FrontCategoryEntity>)
                    redisTemplate.boundValueOps(CATEGORY_TREE_KEY).get();
            return treeVO;
        }
        return null;
    }
}
