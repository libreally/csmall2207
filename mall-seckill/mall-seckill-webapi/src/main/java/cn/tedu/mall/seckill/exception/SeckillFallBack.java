package cn.tedu.mall.seckill.exception;

import cn.tedu.mall.common.restful.JsonResult;
import cn.tedu.mall.common.restful.ResponseCode;
import cn.tedu.mall.pojo.seckill.dto.SeckillOrderAddDTO;
import lombok.extern.slf4j.Slf4j;

// 秒杀业务的降级处理类
@Slf4j
public class SeckillFallBack {

    // 降级方法参数和返回值和限流方法大部分一致,唯一区别就是异常类型换位Throwable类型
    public static JsonResult seckillFallBack(String randCode,
                                             SeckillOrderAddDTO seckillOrderAddDTO,
                                             Throwable e){
        log.error("一个请求被降级了");
        // 输出异常信息
        e.printStackTrace();
        return JsonResult.failed(ResponseCode.INTERNAL_SERVER_ERROR,
                "发生异常,异常信息为:"+e.getMessage());
    }




}
