package cn.tedu.mall.seckill.timer.job;


import cn.tedu.mall.pojo.seckill.model.SeckillSku;
import cn.tedu.mall.pojo.seckill.model.SeckillSpu;
import cn.tedu.mall.seckill.mapper.SeckillSkuMapper;
import cn.tedu.mall.seckill.mapper.SeckillSpuMapper;
import cn.tedu.mall.seckill.utils.SeckillCacheUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.math.RandomUtils;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
public class SeckillInitialJob implements Job {

     @Autowired
     private SeckillSpuMapper spuMapper;
     @Autowired
     private SeckillSkuMapper skuMapper;
     @Autowired
     private RedisTemplate redisTemplate;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

     /*
    RedisTemplate对象在保存数据到Redis时,会将数据先序列化之后再保存
    这样做,对java对象或类似的数据在Redis中的读写效率高,缺点是不能在redis中修改这个数据
    现在我们要保存的是秒杀sku的库存数,如果这个数也用RedisTemplate保存,也会有上面的问题
    容易在高并发的情况下,由于线程安全问题导致"超卖"
    解决办法就是我们需要创建一个能够直接在Redis中修改数据的对象,避免线程安全问题防止"超卖"
    SpringDataRedis提供了StringRedisTemplate类型,它是可以直接操作redis中字符串的
    使用StringRedisTemplate向Redis保存数据,直接存字符串值,没有序列化过程
    而且它支持java中直接发送指令修改数值类型的内容,所以适合保存库存数
    这样就避免了java代码中对库存数修改带来的线程安全问题
     */

    @Override
    public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
        // 当前方法是执行缓存预热工作的
        // 本方法运行的时机是秒杀开始前5分钟,所以要获取5分钟后进行秒杀的所有商品
        LocalDateTime time=LocalDateTime.now().plusMinutes(5);
        // 查询这个时间要进行秒杀的所有商品
        List<SeckillSpu> seckillSpus=spuMapper.findSeckillSpuByTime(time);
        // 遍历本批次秒杀商品的集合
        for(SeckillSpu spu : seckillSpus){
            // 第一个目标是将本批次所有spu对应的sku库存数预热到redis
            // 先根据spu对象的spuId去查询对应的sku列表
            List<SeckillSku> seckillSkus=skuMapper
                    .findSeckillSkusById(spu.getSpuId());
            // 遍历seckillSkus集合,获得其中的元素,以及元素中的库存信息
            for(SeckillSku sku : seckillSkus){
                log.info("开始将{}号sku的商品库存数预热到redis",sku.getSkuId());
                // 要操作Redis,先确定保存值用的key
                // SeckillCacheUtils.getStockKey是获取库存字符串常量的方法
                // 方法参数要传入skuId,方法会将这个值追加到常量后
                // skuStockKey最后的值可能为: “mall:seckill:sku:stock:1”
                String skuStockKey= SeckillCacheUtils.getStockKey(sku.getSkuId());
                // 检查Redis中是否已经包含这个Key
                if(redisTemplate.hasKey(skuStockKey)){
                    // 如果这个key已经存在了,证明之前缓存过,直接跳过
                    log.info("{}号sku的库存已经缓存过了",sku.getSkuId());
                }else{
                    // 如果这个key不存在,就需要将当前sku对象的库存数保持到redis
                    // stringRedisTemplate对象直接保存字符串格式的数据,方便后续修改
                    stringRedisTemplate.boundValueOps(skuStockKey)
                            .set(sku.getSeckillStock()+"",
                                    // 秒杀时间  +  提取5分钟+防雪崩随机数30秒  2*60*60*1000+
                                    5*60*1000+ RandomUtils.nextInt(30000),
                                    TimeUnit.MILLISECONDS);
                    log.info("{}号sku商品库存数成功预热到缓存!",sku.getSkuId());
                }
            }
            //预热spu随机码，保存在redis中
            //确定key
            String randCodeKey = SeckillCacheUtils.getRandCodeKey(spu.getSpuId());
            //判断key中是否已经存在redis
            if (redisTemplate.hasKey(randCodeKey)){
                int randCode = (int)redisTemplate.boundValueOps(randCodeKey).get();
                log.info("{}号spu商品的随机码已经缓存过，值为:{}",spu.getSpuId(),randCode);
            }else{
                int randCode=RandomUtils.nextInt(900000)+100000;
                redisTemplate.boundValueOps(randCodeKey)
                        .set(randCode,
                                5*60*1000+RandomUtils.nextInt(10000),
                                TimeUnit.MILLISECONDS);
                log.info("spuId为{}号的随机码生成成功 值为：{}",spu.getSpuId(),randCode);
            }
        }
    }
}
