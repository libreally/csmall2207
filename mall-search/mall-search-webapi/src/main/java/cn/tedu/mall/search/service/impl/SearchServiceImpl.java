package cn.tedu.mall.search.service.impl;

import cn.tedu.mall.common.restful.JsonPage;
import cn.tedu.mall.pojo.product.model.Spu;
import cn.tedu.mall.pojo.search.entity.SpuForElastic;
import cn.tedu.mall.product.service.front.IForFrontSpuService;
import cn.tedu.mall.search.repository.SpuForElasticRepository;
import cn.tedu.mall.search.service.ISearchService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class SearchServiceImpl implements ISearchService {

    // dubbo调用product模块分页查询所有spu的方法
    @DubboReference
    private IForFrontSpuService dubboSpuService;
    @Autowired
    private SpuForElasticRepository spuRepository;

    @Override
    public void loadSpuByPage() {
        // 这个方法需要循环调用分页查询所有spu数据的方法,直到将所有数据都查出
        // 每次循环的操作就是将当前从数据库中查询的数据新增到ES
        // 循环条件应该是总页数,但是总页数需要查询一次之后才能得知,所以我们使用do-while循环
        int i=1;     // 循环变量,从1开始,因为可以直接当页码使用
        int page;  // 总页数,也是循环条件,是循环操作运行一次之后会被赋值,这里赋默认值或不赋值皆可

        do{
            // dubbo调用查询当前页的spu数据
            JsonPage<Spu> spus=dubboSpuService.getSpuByPage(i,2);
            // 查询出的List是Spu类型,不能直接新增到ES中,需要转换为SpuForElastic
            List<SpuForElastic> esSpus=new ArrayList<>();
            // 遍历分页查询出的数据库的集合
            for(Spu spu : spus.getList()){
                // 下面开始转换,实例化新实体类,并将同名属性赋值
                SpuForElastic esSpu=new SpuForElastic();
                BeanUtils.copyProperties(spu,esSpu);
                // 将esSpu新增到集合中
                esSpus.add(esSpu);
            }
            // esSpus集合中已经包含了本页所有数据,利用提供的批量新增完成新增到ES的操作
            spuRepository.saveAll(esSpus);
            log.info("成功加载了第{}页数据",i);
            // 为下次循环做自增
            i++;
            // 为page(总页数)赋值
            page=spus.getTotalPage();
        }while (i<=page);
    }

    // 根据用户指定的关键字分页查询ES中商品信息
    @Override
    public JsonPage<SpuForElastic> search(
            String keyword, Integer page, Integer pageSize) {
        // 根据参数中的分页数据,执行分页查询,注意SpringData分页页码从0开始
        Page<SpuForElastic> spus= (Page<SpuForElastic>) spuRepository.querySearch(
                keyword, PageRequest.of(page-1,pageSize));
        // 分页查询调用结束返回Page类型对象,我们要求返回JsonPage类型做统一分页查询的返回
        JsonPage<SpuForElastic> jsonPage=new JsonPage<>();
        // 赋值分页信息
        jsonPage.setPage(page);
        jsonPage.setPageSize(pageSize);
        jsonPage.setTotalPage(spus.getTotalPages());
        jsonPage.setTotal(spus.getTotalElements());
        // 赋值分页数据
        jsonPage.setList(spus.getContent());
        // 最后返回!!!
        return jsonPage;
    }


}
