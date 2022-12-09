package cn.tedu.mall.seckill.controller;

import cn.tedu.mall.common.exception.CoolSharkServiceException;
import cn.tedu.mall.common.restful.JsonResult;
import cn.tedu.mall.common.restful.ResponseCode;
import cn.tedu.mall.pojo.seckill.dto.SeckillOrderAddDTO;
import cn.tedu.mall.pojo.seckill.vo.SeckillCommitVO;
import cn.tedu.mall.seckill.exception.SeckillBlockHandler;
import cn.tedu.mall.seckill.exception.SeckillFallBack;
import cn.tedu.mall.seckill.service.ISeckillService;
import cn.tedu.mall.seckill.utils.SeckillCacheUtils;
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 验证随机码
 */
@RestController
@RequestMapping("/seckill")
@Api(tags = "执行秒杀模块")
public class SeckillController {
    @Autowired
    private ISeckillService serviceService;
    @Autowired
    private RedisTemplate redisTemplate;

    @PostMapping("{randCode}")
    @ApiOperation("验证随机码并提交秒杀订单")
    @ApiImplicitParam(value = "随机码",name = "randCode",required = true)
    @PreAuthorize("hasRole('user')")
    @SentinelResource(value = "seckill",
        blockHandlerClass = SeckillBlockHandler.class,
        blockHandler = "seckillBlock",
        fallbackClass = SeckillFallBack.class,
        fallback = "seckillFallBack")
    public JsonResult<SeckillCommitVO> commitSeckill(
            @PathVariable Long randCode,
            @Validated SeckillOrderAddDTO seckillOrderAddDTO){
        //先获取spuId
        Long spuId = seckillOrderAddDTO.getSpuId();
        //获得这个souId对应随机码的key
        String randCodeKey = SeckillCacheUtils.getRandCodeKey(spuId);
        //判断redis中是否有这个key
        if (redisTemplate.hasKey(randCodeKey)){
            //取出这个key的value 即获得随机码
            String redisRandCode = redisTemplate.boundValueOps(randCodeKey).get() + "";
            //判断前端发来的随机码是否和redis中的随机码是否一致
            if (!redisRandCode.equals(randCode)){
                throw new CoolSharkServiceException(ResponseCode.NOT_FOUND,"没有此商品!!!");
            }
            //随机码匹配调用业务层
            SeckillCommitVO seckillCommitVO = serviceService.commitSeckill(seckillOrderAddDTO);
            return JsonResult.ok(seckillCommitVO);
        }else{
            //美没有的情况
            throw new CoolSharkServiceException(ResponseCode.NOT_FOUND,"没有此商品！");
        }
    }
}
