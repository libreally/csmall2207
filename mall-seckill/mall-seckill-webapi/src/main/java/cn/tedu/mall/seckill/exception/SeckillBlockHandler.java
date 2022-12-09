package cn.tedu.mall.seckill.exception;

import cn.tedu.mall.common.restful.JsonResult;
import cn.tedu.mall.common.restful.ResponseCode;
import cn.tedu.mall.pojo.seckill.dto.SeckillOrderAddDTO;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import lombok.extern.slf4j.Slf4j;

// 秒杀业务限流异常处理类
@Slf4j
public class SeckillBlockHandler {

    // 声明限流的方法,返回值必须和被限流的控制器方法一致
    // 参数要包含全部控制器方法参数,还要在参数最后添加BlockException的声明
    // 当限流\降级方法和目标控制器方法不再同一个类中时,限流和降级方法要声明为static,否则报错
    public static JsonResult seckillBlock(String randCode,
                                          SeckillOrderAddDTO seckillOrderAddDTO,
                                          BlockException e){
        log.error("一个请求被限流了!");
        return JsonResult.failed(ResponseCode.INTERNAL_SERVER_ERROR,
                "对不起服务器忙,请稍后再试");
    }



}
