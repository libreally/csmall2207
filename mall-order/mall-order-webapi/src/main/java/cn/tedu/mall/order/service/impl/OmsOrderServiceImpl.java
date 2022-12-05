package cn.tedu.mall.order.service.impl;

import cn.tedu.mall.common.exception.CoolSharkServiceException;
import cn.tedu.mall.common.pojo.domain.CsmallAuthenticationInfo;
import cn.tedu.mall.common.restful.JsonPage;
import cn.tedu.mall.common.restful.ResponseCode;
import cn.tedu.mall.order.mapper.OmsOrderItemMapper;
import cn.tedu.mall.order.mapper.OmsOrderMapper;
import cn.tedu.mall.order.service.IOmsCartService;
import cn.tedu.mall.order.service.IOmsOrderService;
import cn.tedu.mall.order.utils.IdGeneratorUtils;
import cn.tedu.mall.pojo.order.dto.OrderAddDTO;
import cn.tedu.mall.pojo.order.dto.OrderItemAddDTO;
import cn.tedu.mall.pojo.order.dto.OrderListTimeDTO;
import cn.tedu.mall.pojo.order.dto.OrderStateUpdateDTO;
import cn.tedu.mall.pojo.order.model.OmsCart;
import cn.tedu.mall.pojo.order.model.OmsOrder;
import cn.tedu.mall.pojo.order.model.OmsOrderItem;
import cn.tedu.mall.pojo.order.vo.OrderAddVO;
import cn.tedu.mall.pojo.order.vo.OrderDetailVO;
import cn.tedu.mall.pojo.order.vo.OrderListVO;
import cn.tedu.mall.product.service.order.IForOrderSkuService;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import io.seata.spring.annotation.GlobalTransactional;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@DubboService
public class OmsOrderServiceImpl implements IOmsOrderService {
    // dubbo调用减少库存数的方法
    @DubboReference
    private IForOrderSkuService dubboSkuService;
    @Autowired
    private IOmsCartService omsCartService;
    @Autowired
    private OmsOrderMapper omsOrderMapper;
    @Autowired
    private OmsOrderItemMapper omsOrderItemMapper;

    // 新增订单的方法
    // 这个方法dubbo调用了product模块的方法,操作了数据库,有分布式事务的需求
    // 需要使用注解激活Seata分布式事务的功能
    @GlobalTransactional
    @Override
    public OrderAddVO addOrder(OrderAddDTO orderAddDTO) {
        // 第一部分:收集信息,准备数据
        // 先实例化OmsOrder对象
        OmsOrder order = new OmsOrder();
        // 将参数orderAddDTO同名属性赋值到order对象中
        BeanUtils.copyProperties(orderAddDTO, order);
        // orderAddDTO中实际上只有一部分内容,order对象属性并不完全
        // 所以我们要编写一个方法,完成order未赋值属性的收集或生成
        loadOrder(order);
        // 到此为止,order对象的所有信息就收集完毕了
        // 下面要将参数orderAddDTO中包含的订单项集合属性:orderItems进行信息收集和赋值
        // 先从参数中获得这个集合
        List<OrderItemAddDTO> itemAddDTOs = orderAddDTO.getOrderItems();
        if (itemAddDTOs == null || itemAddDTOs.isEmpty()) {
            // 如果订单项集合中没有商品,抛出异常终止程序
            throw new CoolSharkServiceException(ResponseCode.BAD_REQUEST,
                    "订单中必须至少包含一件商品");
        }
        // 我们最终的目标是将当前订单中包含的订单项新增到数据库
        // 当前集合泛型类型是OrderItemAddDTO,而我们向数据库进行订单项批量新增操作的泛型是OmsOrderItem
        // 所以我们要编写将上面集合转换为List<OmsOrderItem>类型的方法
        List<OmsOrderItem> omsOrderItems = new ArrayList<>();
        // 遍历itemAddDTOs
        for (OrderItemAddDTO addDTO : itemAddDTOs) {
            // 实例化最终需要的OmsOrderItem类型对象
            OmsOrderItem orderItem = new OmsOrderItem();
            // 将addDTO同名属性赋值到orderItem中
            BeanUtils.copyProperties(addDTO, orderItem);
            // 将addDTO对象中没有的id属性和orderId属性赋值
            // 利用Leaf生成订单项的id并赋值
            Long itemId = IdGeneratorUtils.getDistributeId("order_item");
            orderItem.setId(itemId);
            // 赋值订单id
            orderItem.setOrderId(order.getId());
            // orderItem的所有值赋值完成,将这个对象添加到集合中
            omsOrderItems.add(orderItem);
            // 第二部分:执行数据库操作指令
            // 1.减少库存
            // 当前正在遍历的对象就是一个包含skuId和减少库存数的对象
            // 先获取skuId
            Long skuId = orderItem.getSkuId();
            // dubbo调用减少库存的方法
            int rows = dubboSkuService.reduceStockNum(skuId, orderItem.getQuantity());
            // 判断rows值是否为0
            if (rows == 0) {
                log.error("商品库存不足,skuId:{}", skuId);
                // 抛出异常终止程序,触发seata分布式事务回滚
                throw new CoolSharkServiceException(ResponseCode.BAD_REQUEST,
                        "库存不足");
            }
            // 2.删除勾选的购物车的商品
            OmsCart omsCart = new OmsCart();
            omsCart.setUserId(order.getUserId());
            omsCart.setSkuId(skuId);
            // 执行删除
            omsCartService.removeUserCarts(omsCart);
        }
        //3.执行新增订单
        omsOrderMapper.insertOrder(order);
        //4.新增订单项
        omsOrderItemMapper.insertOrderItemList(omsOrderItems);

        // 第三部分:准备返回值,返回给前端
        OrderAddVO addVO = new OrderAddVO();
        addVO.setId(order.getId());
        addVO.setSn(order.getSn());
        addVO.setCreateTime(order.getGmtCreate());
        addVO.setPayAmount(order.getAmountOfActualPay());
        return addVO;
    }

    // 为order对象补全属性值的方法
    private void loadOrder(OmsOrder order) {
        // 我们设计新增订单的功能使用Leaf分布式序列生成系统
        Long id = IdGeneratorUtils.getDistributeId("order");
        order.setId(id);
        // 生成用户看到的订单编号,即生成UUID
        order.setSn(UUID.randomUUID().toString());
        // 赋值userId
        // 以后秒杀业务会调用这个方法,userId属性会被赋值
        // 被远程调用时,当前方法无法获取登录用户信息,所以要判断一下order的userId是否为null
        if (order.getUserId() == null) {
            // 从SpringSecurity上下文中获取当前登录用户id
            order.setUserId(getUserId());
        }

        // 判断订单状态,如果为null,设置默认值为0
        if (order.getState() == null) {
            order.setState(0);
        }

        // 为了保证下单时间gmt_order和数据创建gmt_create时间一致
        // 我们在代码中为它们赋相同的值
        LocalDateTime now = LocalDateTime.now();
        order.setGmtOrder(now);
        order.setGmtCreate(now);
        order.setGmtModified(now);

        // 后端代码对实际应付金额进行验算,以求和前端数据一致
        // 实际应付金额=原价-优惠+运费
        // 金钱相关数据使用BigDecimal类型,防止浮点偏移的误差,取消取值范围限制
        BigDecimal price = order.getAmountOfOriginalPrice();
        BigDecimal freight = order.getAmountOfFreight();
        BigDecimal discount = order.getAmountOfDiscount();
        BigDecimal actualPay = price.subtract(discount).add(freight);
        // 最后将计算完成的金额赋值到order对象
        order.setAmountOfActualPay(actualPay);
    }

    @Override
    public void updateOrderState(OrderStateUpdateDTO orderStateUpdateDTO) {

    }

    // 分页和查询当前登录用户,指定时间范围内所有订单
    // 默认查询最近一个月订单的信息,查询的返回值OrderListVO,是包含订单和订单中商品信息的对象
    // 查询OrderListVO的持久层是查询多张表返回值的特殊关联查询
    @Override
    public JsonPage<OrderListVO> listOrdersBetweenTimes(OrderListTimeDTO orderListTimeDTO) {
        // 方法一开始,需要先确定查询的时间范围,默认是一个月内
        // 要判断orderListTimeDTO参数中开始时间和结束时间是否有null值
        validateTimeAndLoadTime(orderListTimeDTO);
        // 将userId赋值到参数中
        orderListTimeDTO.setUserId(getUserId());
        // 设置分页条件
        PageHelper.startPage(orderListTimeDTO.getPage(),
                orderListTimeDTO.getPageSize());
        List<OrderListVO> list=omsOrderMapper
                .selectOrdersBetweenTimes(orderListTimeDTO);
        // 最后返回JsonPage
        return JsonPage.restPage(new PageInfo<>(list));
    }

    private void validateTimeAndLoadTime(OrderListTimeDTO orderListTimeDTO) {
        // 获取参数对象中的开始时间和结束时间
        LocalDateTime start=orderListTimeDTO.getStartTime();
        LocalDateTime end= orderListTimeDTO.getEndTime();
        // 为了使业务更简单,我们设计start或end任意一个值为null,就查询最近一个月订单
        if(start == null  ||  end == null){
            // start设置为一个月前的时间
            start=LocalDateTime.now().minusMonths(1);
            // end设置为现在即可
            end=LocalDateTime.now();
            // 将确定好值的时间赋到参数中
            orderListTimeDTO.setStartTime(start);
            orderListTimeDTO.setEndTime(end);
        }else{
            // 如果start和end都非null
            // 就要判断,如果end小于了start就要抛出异常
            if(end.toInstant(ZoneOffset.of("+8")).toEpochMilli()<
                    start.toInstant(ZoneOffset.of("+8")).toEpochMilli()){
                // 如果结束时间小于开始时间,就要抛出异常
                throw new CoolSharkServiceException(ResponseCode.BAD_REQUEST,
                        "结束时间应大于起始时间");
            }
        }
    }

    @Override
    public OrderDetailVO getOrderDetail(Long id) {
        return null;
    }


    public CsmallAuthenticationInfo getUserInfo() {
        //编写springSecurity上下文中获取用户信息代码
        UsernamePasswordAuthenticationToken authenticationToken =
                (UsernamePasswordAuthenticationToken)
                        SecurityContextHolder.getContext().getAuthentication();
        if (authenticationToken == null) {
            throw new CoolSharkServiceException(ResponseCode.UNAUTHORIZED, "未授权登陆");
        }
        //从authenticationToken获取用户信息
        return (CsmallAuthenticationInfo)
                authenticationToken.getCredentials();
    }

    //获取CsmallAuthenticationInfo中的id即userid
    public Long getUserId() {
        return getUserInfo().getId();
    }
}
