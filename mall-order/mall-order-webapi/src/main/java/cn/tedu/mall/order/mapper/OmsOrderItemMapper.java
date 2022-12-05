package cn.tedu.mall.order.mapper;

import cn.tedu.mall.pojo.order.model.OmsOrderItem;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OmsOrderItemMapper {
    //新增订单项oms_order_item ，一次连库，插入多条数据
    int insertOrderItemList(List<OmsOrderItem> omsOrderItems);
}
