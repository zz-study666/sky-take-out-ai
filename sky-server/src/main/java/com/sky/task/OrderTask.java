package com.sky.task;

import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
public class OrderTask {
    @Autowired
    private OrderMapper orderMapper;

    //处理订单超时未支付
    @Scheduled(cron = "0 * * * * ?")//每分钟执行一次
    public void processTimeoutOrders(){
        log.info("处理订单超时未支付：{}", LocalDateTime.now());
        //获取所有状态为待支付的订单
        LocalDateTime time = LocalDateTime.now().minusMinutes(15);//minusMinutes(15)指示15分钟前的时间
        List<Orders> ordersList = orderMapper.getByStatusAndOrderTimeOut(Orders.PENDING_PAYMENT, time);

        //判断订单列表是否为空，如果不为空，则进行订单处理，遍历修改订单状态
        if (ordersList != null && ordersList.size() > 0){
            for (Orders orders : ordersList) {
                log.info("处理订单{}超时未支付，取消订单", orders.getId());
                orders.setStatus(Orders.CANCELLED);
                orders.setCancelReason("支付超时，取消订单");
                orders.setCancelTime(LocalDateTime.now());
                orderMapper.update(orders);
            }
        }



    }

    //处理订单派送中超时未确认
    @Scheduled(cron = "0 0 1 * * ?")//每天凌晨1点钟执行
    public void processDeliveryTimeoutOrders(){
        log.info("处理订单派送中超时未确认：{}", LocalDateTime.now());
        //获取所有状态为派送中的订单
        LocalDateTime time = LocalDateTime.now().minusHours(1);//minusHours(1)指示1小时前时间
        List<Orders> ordersList = orderMapper.getByStatusAndOrderTimeOut(Orders.DELIVERY_IN_PROGRESS, time);
        //判断订单列表是否为空，如果不为空，则进行订单处理，遍历修改订单状态
        if (ordersList != null && ordersList.size() > 0){
            for (Orders orders : ordersList) {
                orders.setStatus(Orders.COMPLETED);
                orderMapper.update(orders);
            }
        }
    }

}
