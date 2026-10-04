package com.sky.rabbitmq;

import com.sky.websocket.WebSocketServer;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static com.sky.config.RabbitConfig.ORDER_NOTIFY_QUEUE;

@Component
public class OrderNotifyConsumer {
    @Autowired
    WebSocketServer webSocketServer;

    // 下单付款通知队列消费者
    @RabbitListener(queues = ORDER_NOTIFY_QUEUE)
    public void consume(String message) {
        webSocketServer.sendToAllClient(message);
    }
}
