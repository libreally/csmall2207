package cn.tedu.mall.seckill.service.impl;

import cn.tedu.mall.pojo.product.vo.SkuStandardVO;
import cn.tedu.mall.pojo.seckill.model.SeckillSku;
import cn.tedu.mall.pojo.seckill.vo.SeckillSkuVO;
import cn.tedu.mall.product.service.seckill.IForSeckillSkuService;
import cn.tedu.mall.seckill.mapper.SeckillSkuMapper;
import cn.tedu.mall.seckill.service.ISeckillSkuService;
import cn.tedu.mall.seckill.utils.SeckillCacheUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.math.RandomUtils;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;


@Slf4j
@Service
public class SeckillSkuServiceImpl implements ISeckillSkuService {
    @Autowired
    private SeckillSkuMapper skuMapper;
    @Autowired
    private RedisTemplate redisTemplate;
    @DubboReference
    private IForSeckillSkuService dubboSkuService;

    @Override
    public List<SeckillSkuVO> listSeckillSkus(Long spuId) {

        List<SeckillSku> seckillSkus = skuMapper.findSeckillSkusById(spuId);

        ArrayList<SeckillSkuVO> seckillSkuVOS = new ArrayList<>();

        for (SeckillSku sku : seckillSkus) {
            Long skuId = sku.getSkuId();
            //获取key
            String skuVOKey = SeckillCacheUtils.getSeckillSkuVOKey(skuId);
            SeckillSkuVO seckillSkuVO = null;
            if (redisTemplate.hasKey(skuVOKey)) {
                seckillSkuVO = (SeckillSkuVO) redisTemplate.boundValueOps(skuVOKey).get();
            } else {
                SkuStandardVO skuStandardVO = dubboSkuService.getById(skuId);
                seckillSkuVO = new SeckillSkuVO();
                BeanUtils.copyProperties(skuStandardVO, seckillSkuVO);

                seckillSkuVO.setSeckillPrice(sku.getSeckillPrice());
                seckillSkuVO.setStock(sku.getSeckillStock());
                seckillSkuVO.setSeckillLimit(sku.getSeckillLimit());

                redisTemplate.boundValueOps(skuVOKey)
                        .set(seckillSkuVO, 5 * 60 * 100 + RandomUtils.nextInt(10000), TimeUnit.MILLISECONDS);

            }
            seckillSkuVOS.add(seckillSkuVO);
        }

        return seckillSkuVOS;
    }
}
