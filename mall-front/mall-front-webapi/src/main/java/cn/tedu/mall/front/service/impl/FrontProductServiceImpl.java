package cn.tedu.mall.front.service.impl;

import cn.tedu.mall.common.restful.JsonPage;
import cn.tedu.mall.front.service.IFrontProductService;
import cn.tedu.mall.pojo.product.vo.*;
import cn.tedu.mall.product.service.front.IForFrontAttributeService;
import cn.tedu.mall.product.service.front.IForFrontSkuService;
import cn.tedu.mall.product.service.front.IForFrontSpuService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class FrontProductServiceImpl implements IFrontProductService {
    @DubboReference
    private IForFrontSpuService dubboSpuService;
    // 根据spuId查询sku信息的dubbo调用对象
    @DubboReference
    private IForFrontSkuService dubboSkuService;
    // 根据spuId查询属性的dubbo调用对象
    @DubboReference
    private IForFrontAttributeService dubboAttributeService;
    //根据分类id分页查询spu列表
    @Override
    public JsonPage<SpuListItemVO> listSpuByCategoryId(Long categoryId, Integer page, Integer pageSize) {
        JsonPage<SpuListItemVO> jsonPage = dubboSpuService.listSpuByCategoryId(categoryId, page, pageSize);
        return jsonPage;
    }

    //根据分类id分页查询spuStandard
    @Override
    public SpuStandardVO getFrontSpuById(Long id) {
        SpuStandardVO spuStandardVO = dubboSpuService.getSpuById(id);
        return spuStandardVO;
    }
    //根据spuId查询sku列表
    @Override
    public List<SkuStandardVO> getFrontSkusBySpuId(Long spuId) {
        List<SkuStandardVO> list = dubboSkuService.getSkusBySpuId(spuId);
        return list;
    }
    //根据spuId查询spuDetail
    @Override
    public SpuDetailStandardVO getSpuDetail(Long spuId) {
        SpuDetailStandardVO spuDetailStandardVO = dubboSpuService.getSpuDetailById(spuId);
        return spuDetailStandardVO;
    }
    //根据spuId查询商品的属性与规格列表
    @Override
    public List<AttributeStandardVO> getSpuAttributesBySpuId(Long spuId) {
        List<AttributeStandardVO> list = dubboAttributeService.getSpuAttributesBySpuId(spuId);
        return list;
    }
}
