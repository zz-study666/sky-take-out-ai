package com.sky.rabbitmq;

import com.rabbitmq.client.Channel;
import com.sky.config.RabbitConfig;
import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.time.LocalDateTime;

@Slf4j
@Component
public class OrderDelayConsumer {

    @Resource
    private OrderMapper orderMapper;

    // 监听死信队列
    @RabbitListener(queues = RabbitConfig.DLX_QUEUE)
    public void listenDlQueue(Long orderId, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        try {
            log.info("处理订单超时未支付：{}", LocalDateTime.now());
            // 1. 根据订单id查询订单
            Orders order = orderMapper.getById(orderId);
            // 2. 判断：只有待支付才取消，如果已经支付，直接放行
            if(order != null && Orders.PENDING_PAYMENT.equals(order.getStatus())){
                log.info("处理订单{}超时未支付，取消订单", order.getId());
                order.setStatus(Orders.CANCELLED);
                order.setCancelReason("支付超时，自动取消");
                order.setCancelTime(LocalDateTime.now());
                orderMapper.update(order);
            }
            // 手动ACK，告知MQ消费成功
            channel.basicAck(tag, false);
        }catch (Exception e){
            // 消费异常，nack，可根据业务选择重回队列或者丢弃
            channel.basicNack(tag, false, false);
            log.info("处理订单超时未支付异常，订单id：{}", orderId);
            e.printStackTrace();
        }
    }
}