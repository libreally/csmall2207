package cn.tedu.mall.seckill.service.impl;

import cn.tedu.mall.common.exception.CoolSharkServiceException;
import cn.tedu.mall.common.pojo.domain.CsmallAuthenticationInfo;
import cn.tedu.mall.common.restful.ResponseCode;
import cn.tedu.mall.order.service.IOmsOrderService;
import cn.tedu.mall.pojo.order.dto.OrderAddDTO;
import cn.tedu.mall.pojo.order.dto.OrderItemAddDTO;
import cn.tedu.mall.pojo.order.vo.OrderAddVO;
import cn.tedu.mall.pojo.seckill.dto.SeckillOrderAddDTO;
import cn.tedu.mall.pojo.seckill.model.Success;
import cn.tedu.mall.pojo.seckill.vo.SeckillCommitVO;
import cn.tedu.mall.seckill.config.RabbitMqComponentConfiguration;
import cn.tedu.mall.seckill.service.ISeckillService;
import cn.tedu.mall.seckill.utils.SeckillCacheUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class SeckillServiceImpl implements ISeckillService {

    // 秒杀业务中,使用Redis的代码都是在判断数值,直接使用字符串类型的Redis对象
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    // 需要dubbo调用mall_order模块的普通订单生成业务
    @DubboReference
    private IOmsOrderService dubboOrderService;
    //需要将秒杀成功信息发送给消息队列
    @Autowired
    private RabbitTemplate rabbitTemplate;

    /*
    1.判断用户是否为重复购买和Redis中该Sku是否有库存
    2.秒杀订单转换成普通订单,需要使用dubbo调用order模块的生成订单方法
    3.使用消息队列(RabbitMQ)将秒杀成功记录信息保存到success表中
    4.秒杀订单信息返回
    */
    @Override
    public SeckillCommitVO commitSeckill(SeckillOrderAddDTO seckillOrderAddDTO) {
        // 第一阶段:判断用户是否为重复购买和Redis中该Sku是否有库存
        // 从方法的参数中,获得用户想要购买的skuId
        Long skuId=seckillOrderAddDTO.getSeckillOrderItemAddDTO().getSkuId();
        // 从SpringSecurity上下文中获得用户Id
        Long userId=getUserId();
        // 我们明确了本次请求是哪个用户要购买哪个商品(userId和skuId的值)
        // 根据秒杀业务限制,每个用户只能购买skuId一次
        // 所以可以根据userId和skuId生成检查重复购买的key
        // mall:seckill:reseckill:2:1
        String reSeckillCheckKey= SeckillCacheUtils.getReseckillCheckKey(skuId,userId);
        // 用上面字符串做key,向redis中发送命令使用stringRedisTemplate的increment方法
        // increment是增长的意思,这个方法效果如下
        // 1.如果上面的key在redis中不存在,redis中会使用这个key,创建一个值,值为1
        // 2.如果上面的key已经在redis中,那么就会在当前的值基础上加1再保存,例如当前已经是1,会变为2
        // 3.无论当前key存在不存在,都会将处理之后的值返回
        // 综上,只要用户调用这个方法返回值为1,就表示这个用户没有买过这个商品
        Long seckillTimes=stringRedisTemplate.
                boundValueOps(reSeckillCheckKey).increment();
        // 如果seckillTimes值大于1,就是用户已经购买过这个商品
        if(seckillTimes>1){
            // 抛出异常,提示不能重复购买,终止程序
            throw new CoolSharkServiceException(ResponseCode.FORBIDDEN,
                    "您已经购买过这个商品了,谢谢您的支持");
        }
        // 程序运行到此处,表示当前用户第一次购买这个商品
        // 然后检查这个商品是否有库存
        // 根据要购买的skuId,获得这个sku在redis中的Key
        // mall:seckill:sku:stock:1
        String skuStockKey=SeckillCacheUtils.getStockKey(skuId);
        // 如果Redis中没有这个key要抛出异常
        if(!stringRedisTemplate.hasKey(skuStockKey)){
            throw new CoolSharkServiceException(ResponseCode.INTERNAL_SERVER_ERROR,
                    "缓存中没有库存信息,购买失败!");
        }
        // 和上面的increment方法相反,
        // 这里调用的decrement(减少)方法是将当前key保存在redis中的值减1之后返回
        Long leftStock=stringRedisTemplate.boundValueOps(skuStockKey).decrement();
        // leftStock是decrement方法对当前值减1之后返回的
        // 所以leftStock的值表示的是当前用户购买后剩余的库存数
        // 既leftStock等于0时,当前用户买到了最后一个,leftStock小于0,才是已经没有库存了
        if(leftStock<0){
            // 没有库存了,要抛出异常终止程序
            // 但是要先将当前用户购买这个商品的记录恢复为0
            stringRedisTemplate.boundValueOps(reSeckillCheckKey).decrement();
            throw new CoolSharkServiceException(ResponseCode.BAD_REQUEST,
                    "对不起,您要购买的商品暂时售罄");
        }
        // 到此为止,用户通过了重复购买和库存数的判断,可以开始生产订单了
        // 第二阶段:秒杀订单转换成普通订单,需要使用dubbo调用order模块的生成订单方法
        // 当前方法参数类型SeckillOrderAddDTO,dubbo调用需要的参数类型OrderAddDTO
        // 下面开始进行转换,转换代码较多,单独编写方法
        OrderAddDTO orderAddDTO=convertSeckillOrderToOrder(seckillOrderAddDTO);
        // 完成了转换操作,订单的所有属性都赋值完毕了,除了userId
        // 秒杀模块当前获得的userId值必须赋值到orderAddDTO中
        orderAddDTO.setUserId(userId);
        // dubbo调用生成订单的方法
        OrderAddVO orderAddVO=dubboOrderService.addOrder(orderAddDTO);
        // 第三阶段:使用消息队列(RabbitMQ)将秒杀成功记录信息保存到success表中
        // 业务要求我们记录秒杀成功信息,但是它不是迫切运行的,所以可以使用消息队列完成
        // 我们要创建Success秒杀记录对象,然后将它发送给RabbitMQ
        // 另寻时机编写处理消息队列的代码
        Success success=new Success();
        // Success大部分属性和秒杀Sku信息重叠,可以做同名属性赋值的操作
        BeanUtils.copyProperties(seckillOrderAddDTO.getSeckillOrderItemAddDTO(),
                success);
        // 将不足的信息补全
        success.setUserId(userId);
        success.setOrderSn(orderAddVO.getSn());
        success.setSeckillPrice(seckillOrderAddDTO.getSeckillOrderItemAddDTO()
                .getPrice());
        // success对象信息完备,执行发送给RabbitMQ
        rabbitTemplate.convertAndSend(
                RabbitMqComponentConfiguration.SECKILL_EX,
                RabbitMqComponentConfiguration.SECKILL_RK,
                success);
        // 这里只做消息的发送,无需考虑消息接收的问题
        // 第四阶段:秒杀订单信息返回
        // 当前方法要求返回的类型是SeckillCommitVO
        // 经观察,这个类和我们提交普通订单得到的返回值OrderAddVO属性完全一致
        // 所以做同名属性赋值之后,直接返回即可
        SeckillCommitVO commitVO=new SeckillCommitVO();
        BeanUtils.copyProperties(orderAddVO,commitVO);
        // 别忘了返回!!!!
        return commitVO;
    }
    // 秒杀订单转换成普通订单的方法
    private OrderAddDTO convertSeckillOrderToOrder(
            SeckillOrderAddDTO seckillOrderAddDTO) {
        // 实例化返回值对象
        OrderAddDTO orderAddDTO=new OrderAddDTO();
        // 将参数seckillOrderAddDTO的同名属性赋值到orderAddDTO
        BeanUtils.copyProperties(seckillOrderAddDTO,orderAddDTO);
        // 经过上面操作,大部分数据都已经完成赋值,区别主要在于两个订单对象包含的订单项
        // OrderAddDTO是普通订单对象,其中的订单项属性是一个集合List<OrderItemAddDTO>
        // 而SeckillOrderAddDTO是秒杀订单对象,其中的订单项属性是一个对象SeckillOrderItemAddDTO
        // 所以我们先将SeckillOrderItemAddDTO对象转化为普通订单项类型OrderItemAddDTO
        OrderItemAddDTO orderItemAddDTO=new OrderItemAddDTO();
        BeanUtils.copyProperties(seckillOrderAddDTO.getSeckillOrderItemAddDTO(),
                orderItemAddDTO);
        // 再实例化普通订单项集合List<OrderItemAddDTO>
        List<OrderItemAddDTO> list=new ArrayList<>();
        // 然后将转换好的普通订单项添加到集合中
        list.add(orderItemAddDTO);
        // 最后将添加完对象的订单项集合赋值到orderAddDTO中
        orderAddDTO.setOrderItems(list);
        // 完成了!返回转换结果
        return orderAddDTO;

    }

    public CsmallAuthenticationInfo getUserInfo(){
        // 编写获取SpringSecurity上下文的代码
        UsernamePasswordAuthenticationToken authenticationToken=
                (UsernamePasswordAuthenticationToken)
                        SecurityContextHolder.getContext().getAuthentication();
        // 为了逻辑严谨,判断一下SpringSecurity上下文中信息是不是null
        if(authenticationToken == null){
            throw new CoolSharkServiceException(
                    ResponseCode.UNAUTHORIZED,"您没有登录!");
        }
        // 从SpringSecurity上下文中获得用户信息
        CsmallAuthenticationInfo csmallAuthenticationInfo=
                (CsmallAuthenticationInfo) authenticationToken.getCredentials();
        // 最终别忘了返回
        return csmallAuthenticationInfo;
    }
    // 业务逻辑层需求中,实际上只需要用户的id
    // 我们可以再编写一个方法,从用户对象中获取id
    public Long getUserId(){
        return getUserInfo().getId();
    }
}
