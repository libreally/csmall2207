package cn.tedu.mall.seckill.service.impl;

import cn.tedu.mall.common.exception.CoolSharkServiceException;
import cn.tedu.mall.common.restful.JsonPage;
import cn.tedu.mall.common.restful.ResponseCode;
import cn.tedu.mall.pojo.product.vo.SpuDetailStandardVO;
import cn.tedu.mall.pojo.product.vo.SpuStandardVO;
import cn.tedu.mall.pojo.seckill.model.SeckillSpu;
import cn.tedu.mall.pojo.seckill.vo.SeckillSpuDetailSimpleVO;
import cn.tedu.mall.pojo.seckill.vo.SeckillSpuVO;
import cn.tedu.mall.product.service.seckill.IForSeckillSpuService;
import cn.tedu.mall.seckill.mapper.SeckillSpuMapper;
import cn.tedu.mall.seckill.service.ISeckillSpuService;
import cn.tedu.mall.seckill.utils.SeckillCacheUtils;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.math.RandomUtils;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class SeckillSpuServiceImpl implements ISeckillSpuService {

    //装配redis操作对象
    @Autowired
    private RedisTemplate redisTemplate;
    // 装配查询秒杀表信息的Mapper
    @Autowired
    private SeckillSpuMapper seckillSpuMapper;

    // 查询秒杀列表商品信息的方法中,返回值为SeckillSpuVO,其中包含常规spu信息和秒杀spu信息
    // 所以我们需要dubbo调用product模块,根据spuId查询出spu的常规信息
    @DubboReference
    private IForSeckillSpuService dubboSeckillSpuService;

    // 分页查询秒杀商品信息
    // 注意:返回值集合泛型SeckillSpuVO,既包含常规Spu信息又包含秒杀Spu信息
    @Override
    public JsonPage<SeckillSpuVO> listSeckillSpus(Integer page, Integer pageSize) {
        // 设置分页条件
        PageHelper.startPage(page,pageSize);
        // 执行查询秒杀表中所有商品信息的方法(会自动分页查询)
        List<SeckillSpu> seckillSpus=seckillSpuMapper.findSeckillSpus();
        // 实例化一个SeckillSpuVO泛型的集合,以备后续返回
        List<SeckillSpuVO> seckillSpuVOs=new ArrayList<>();
        // 遍历seckillSpus(没有常规信息的集合)
        for(SeckillSpu seckillSpu : seckillSpus){
            // 获得当前对象的spuId
            Long spuId=seckillSpu.getSpuId();
            // 获得了spuId,利用Dubbo查询这个spu的常规信息
            SpuStandardVO standardVO = dubboSeckillSpuService.getSpuById(spuId);
            // 秒杀信息在seckillSpu对象中,常规信息在standardVO对象中
            // 下面将两方信息都赋值到SeckillSpuVO中
            SeckillSpuVO seckillSpuVO=new SeckillSpuVO();
            // 将常规信息中同名属性赋值到seckillSpuVO
            BeanUtils.copyProperties(standardVO,seckillSpuVO);
            // 秒杀属性单独赋值
            seckillSpuVO.setSeckillListPrice(seckillSpu.getListPrice());
            seckillSpuVO.setStartTime(seckillSpu.getStartTime());
            seckillSpuVO.setEndTime(seckillSpu.getEndTime());
            // 此处seckillSpuVO就已经包含了常规信息和秒杀信息
            // 添加到集合中
            seckillSpuVOs.add(seckillSpuVO);
        }
        // 最后别忘了返回
        return JsonPage.restPage(new PageInfo<>(seckillSpuVOs));
    }
    //根据skuId返回seckillSpuVO对象
    @Override
    public SeckillSpuVO getSeckillSpu(Long spuId) {

        //获取redis对应的key
        String spuVOKey = SeckillCacheUtils.getSeckillSpuVOKey(spuId);
        //声名返回值类型的对象
        SeckillSpuVO seckillSpuVO=null;
        //判断redis中是否包含这个key
        if(redisTemplate.hasKey(spuVOKey)){
            //存在时直接赋值
            seckillSpuVO= (SeckillSpuVO) redisTemplate.boundValueOps(spuVOKey).get();
        }else{
            //不包含key从数据库查询
            //查询秒杀信息和常规信息
            SeckillSpu seckillSpu = seckillSpuMapper.findSeckillSpuById(spuId);
            //判断是否为空.防止布隆过滤器误判
            if(seckillSpu==null){
                throw new CoolSharkServiceException(ResponseCode.NOT_FOUND,"商品不存在");
            }
            // 获取了秒杀信息,再获取常规信息
            SpuStandardVO spuStandardVO = dubboSeckillSpuService.getSpuById(spuId);

            // 开始将秒杀信息和常规信息都赋值到seckillSpuVO中
            seckillSpuVO=new SeckillSpuVO();
            BeanUtils.copyProperties(spuStandardVO,seckillSpuVO);
            // 手动赋值秒杀信息
            seckillSpuVO.setSeckillListPrice(seckillSpu.getListPrice());
            seckillSpuVO.setStartTime(seckillSpu.getStartTime());
            seckillSpuVO.setEndTime(seckillSpu.getEndTime());
            // 将seckillSpuVO保存到Redis中,以便后续直接获取
            redisTemplate.boundValueOps(spuVOKey).set(
                    seckillSpuVO,
                    5*60*1000+ RandomUtils.nextInt(10000),
                    TimeUnit.MILLISECONDS);
        }
        // 到此为止seckillSpuVO只有url属性未被赋值了
        // url属性是要发送给前端的,前端可以使用它来发起生成秒杀订单的请求
        // 所以我们必须先判断当前时间是否在允许秒杀购买该商品的时间段内
        // 获取当前时间
        LocalDateTime nowTime=LocalDateTime.now();
        // 对比时,为了降低系统资源消耗,尽量不连数据库,从seckillSpuVO中获取开始和结束时间
        // 判断逻辑是秒杀开始时间小于当前时间,并且当前时间小于秒杀结束时间
        if(seckillSpuVO.getStartTime().isBefore(nowTime) &&
                nowTime.isBefore(seckillSpuVO.getEndTime())){
            // 进入if表示当前时间是在当前商品秒杀时间段内的,可以为url赋值
            // 我们要从Redis中获取已经预热的随机码
            String randCodeKey=SeckillCacheUtils.getRandCodeKey(spuId);
            // 判断Redis中是否存在这个key
            if(!redisTemplate.hasKey(randCodeKey)){
                // 如果不存在直接抛异常
                throw new CoolSharkServiceException(ResponseCode.NOT_FOUND,"当前随机码不存在");
            }
            // key存在获取随机码
            String randCode=redisTemplate.boundValueOps(randCodeKey).get()+"";
            // 将随机码赋值到url
            seckillSpuVO.setUrl("/seckill/"+randCode);
            log.info("被赋值的url为:{}",seckillSpuVO.getUrl());
        }
        // 别忘了返回!!!!!
        return seckillSpuVO;
    }

    // 项目中没有定义可用的SpuDetail的常量用于Redis的Key
    // 我们就需要自己定义一个
    public static final String SECKILL_SPU_DETAIL_VO_PREFIX="seckill:spu:detail:vo:";
    // 根据spuId查询秒杀SpuDetail
    @Override
    public SeckillSpuDetailSimpleVO getSeckillSpuDetail(Long spuId) {
        // 先获取操作Redis的Key
        String spuDetailKey=SECKILL_SPU_DETAIL_VO_PREFIX+spuId;
        // 先声明一个返回值类型的null对象
        SeckillSpuDetailSimpleVO simpleVO=null;
        // 判断redis中是否包含
        if(redisTemplate.hasKey(spuDetailKey)){
            // 如果存在直接从redis中获取
            simpleVO=(SeckillSpuDetailSimpleVO) redisTemplate
                    .boundValueOps(spuDetailKey).get();
        }else{
            // 如果redis中不存在
            // 利用Dubbo从product模块查询
            SpuDetailStandardVO spuDetailStandardVO =
                    dubboSeckillSpuService.getSpuDetailById(spuId);
            // 实例化simpleVO对象保证它不为null
            simpleVO=new SeckillSpuDetailSimpleVO();
            BeanUtils.copyProperties(spuDetailStandardVO,simpleVO);
            // 保存在Redis中
            redisTemplate.boundValueOps(spuDetailKey)
                    .set(simpleVO,
                            5*60*1000+RandomUtils.nextInt(10000),
                            TimeUnit.MILLISECONDS);
        }
        // 千万别忘了返回
        return simpleVO;
    }
}
