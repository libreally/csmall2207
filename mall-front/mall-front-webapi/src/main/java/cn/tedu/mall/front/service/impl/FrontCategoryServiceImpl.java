package cn.tedu.mall.front.service.impl;

import cn.tedu.mall.common.exception.CoolSharkServiceException;
import cn.tedu.mall.common.restful.ResponseCode;
import cn.tedu.mall.front.service.IFrontCategoryService;
import cn.tedu.mall.pojo.front.entity.FrontCategoryEntity;
import cn.tedu.mall.pojo.front.vo.FrontCategoryTreeVO;
import cn.tedu.mall.pojo.product.vo.CategoryStandardVO;
import cn.tedu.mall.product.service.front.IForFrontCategoryService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;


@Slf4j
@Service
public class FrontCategoryServiceImpl implements IFrontCategoryService {

    //调用product模块
    @DubboReference
    private IForFrontCategoryService dubboCategoryService;

    //操作redis对象
    @Autowired
    private RedisTemplate redisTemplate;
    //redis常量,用于redis中的key
    public static final String CATEGORY_TREE_KEY = "category_tree";

    //返回三级分类树对象的方法
    @Override
    public FrontCategoryTreeVO categoryTree() {
        //先检查redis中是否已经存在了三级分类树对象
        if (redisTemplate.hasKey(CATEGORY_TREE_KEY)) {
            //redis中如已经有了这个key直接获取
            FrontCategoryTreeVO<FrontCategoryEntity> treeVO =
                    (FrontCategoryTreeVO<FrontCategoryEntity>)
                            redisTemplate.boundValueOps(CATEGORY_TREE_KEY).get();
            return treeVO;
        }
        //redis中没有三级分类树信息，表示本次请求时首次访问，需要从 数据库查询再构造三级分类树保存到redis中
        //dubboCategoryService调用查询所有分类
        List<CategoryStandardVO> categoryStandardVOS = dubboCategoryService.getCategoryList();
        // 记住CategoryStandardVO是没有children属性的,FrontCategoryEntity是有的!
        // 下面需要编写一个方法,将子分类对象保存到对应的父分类对象的children属性中
        // 大概思路就是先将CategoryStandardVO转换为FrontCategoryEntity类型,然后再将父子分类关联
        // 整个转换和关联的过程比较复杂,我们编写一个方法来完成
        FrontCategoryTreeVO<FrontCategoryEntity> treeVO = initTree(categoryStandardVOS);
        //上面方法完成三级分类的构建，下面将treeVO存入redis；
        redisTemplate.boundValueOps(CATEGORY_TREE_KEY)
                .set(treeVO,1, TimeUnit.MINUTES);
        return null;
    }

    private FrontCategoryTreeVO<FrontCategoryEntity> initTree(List<CategoryStandardVO> categoryStandardVOS) {
        /* 第一步：
         * 确定所有分类的父分类id
         * 以父分类的id为key，以子分类对象为value保存在Map中
         * 一个父分类可以包含多个子分类对象，所以map的value是个list
         * */
        HashMap<Long, List<FrontCategoryEntity>> map = new HashMap<>();
        log.info("准备构建的三级分类树对象数量为{}", categoryStandardVOS.size());
        //遍历数据库查询所有的分类集合对象
        for (CategoryStandardVO categoryStandardVO : categoryStandardVOS) {
            // 因为CategoryStandardVO对象没有children属性,不能保存关联的子分类对象
            // 所以要将categoryStandardVO中的值赋值给能保存children属性的FrontCategoryEntity对象
            FrontCategoryEntity frontCategoryEntity = new FrontCategoryEntity();
            //同名属性赋值
            BeanUtils.copyProperties(categoryStandardVO, frontCategoryEntity);
            //获取当前分类对象的父分类id，用作map元素的key值（如果父分类id为0，就是一级分类）
            Long parentId = frontCategoryEntity.getParentId();
            //判断这个parentId是否存在map中
            if (!map.containsKey(parentId)) {
                //如果map中没有key为parentId的元素，那么新建元素，确定key和value
                //key就是parentId，value是一个list，要实例化且要存在list保存当前便利的对象
                List<FrontCategoryEntity> value = new ArrayList<>();
                value.add(frontCategoryEntity);
                //最后保存key和value到map
                map.put(parentId, value);
            } else {
                //如果map中有key为parentId的元素。添加进frontCategoryEntity
                map.get(parentId).add(frontCategoryEntity);
            }
        }
        /*
         * 第二步
         * 将子分类添加到对应父分类中的children中
         * 先获取所有一级分类对象也就是parentID为0的对象
         **/
        List<FrontCategoryEntity> firstLevels = map.get(0L);
        //判断一级分类集合如果为空（或没有元素）,抛出异常
        if (firstLevels == null || firstLevels.isEmpty()) {
            throw new CoolSharkServiceException(
                    ResponseCode.INTERNAL_SERVER_ERROR, "没有一级分类对象");
        }
        //遍历一级分类集合
        for (FrontCategoryEntity oneLevel : firstLevels) {
            //一级分类的id就是二级分类的父类的id
            Long secondLevelParentId=oneLevel.getId();
            //根据secondLevelParentId获得这个一级分类的二级分类对象集合
            List<FrontCategoryEntity> secondLevels = map.get(secondLevelParentId);
            //判断二级分类集合如果为空（或没有元素）,抛出异常
            if (secondLevels == null || secondLevels.isEmpty()) {
                //二级分类没有不需要异常
                log.warn("当前分类没有二级分类：{}",secondLevelParentId);
                //如果二级分类没有则可以跳过循环
                continue;
            }
            //遍历二级分类对象集合
            for (FrontCategoryEntity twoLevel : secondLevels){
                //二级分类的id就是三级级分类的父类的id
                Long thirdLevelParentId = twoLevel.getId();
                //根据thirdLevelParentId获得这个二级分类的三级分类对象集合
                List<FrontCategoryEntity> thirdLevels = map.get(thirdLevelParentId);
                //判断三级分类集合如果为空（或没有元素）,抛出异常
                if (thirdLevels == null || thirdLevels.isEmpty()) {
                    //三级分类没有不需要异常
                    log.warn("当前分类没有三级分类：{}",thirdLevelParentId);
                    //如果三级分类没有则可以跳过循环
                    continue;
                }
                //将三级分类对象集合添加到关联的二级分类的children中
                twoLevel.setChildrens(thirdLevels);
            }
            //将二级分类对象集合添加到关联的一级分类的children中
            oneLevel.setChildrens(secondLevels);
        }
        //此时所有对象都确认了子分类之间的关联关系，
        // 最后将一级分类赋值给返回值FrontCategoryTreeVO<FrontCategoryEntity>的list属性
        FrontCategoryTreeVO<FrontCategoryEntity> treeVO = new FrontCategoryTreeVO<>();
        treeVO.setCategories(firstLevels);
        return treeVO;
    }
}
