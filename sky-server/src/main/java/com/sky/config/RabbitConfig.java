package com.sky.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitConfig {
    // 普通交换机、普通队列（存放下单消息，设置TTL15分钟）
    public static final String NORMAL_EXCHANGE = "normal_exchange";
    public static final String NORMAL_QUEUE = "normal_queue";
    public static final String NORMAL_ROUTING_KEY = "normal_rk";

    // 死信交换机、死信队列（超时消息转发到这里，消费者监听）
    public static final String DLX_EXCHANGE = "dlx_exchange";
    public static final String DLX_QUEUE = "dlx_queue";
    public static final String DLX_ROUTING_KEY = "dlx_rk";

    // 下单付款通知交换机队列
    public static final String ORDER_NOTIFY_EXCHANGE = "order_notify_exchange";
    public static final String ORDER_NOTIFY_QUEUE = "order_notify_queue";
    public static final String ORDER_NOTIFY_ROUTING_KEY = "order_notify_rk";



    // 普通交换机
    @Bean
    public DirectExchange normalExchange() {
        return ExchangeBuilder.directExchange(NORMAL_EXCHANGE).durable(true).build();
    }

    // 普通队列：设置TTL、绑定死信交换机
    @Bean
    public Queue normalQueue() {
        Map<String, Object> args = new HashMap<>();
        // 消息过期时间 15分钟 = 900000毫秒
        args.put("x-message-ttl", 30000);//30秒测试
        // 指定死信交换机
        args.put("x-dead-letter-exchange", DLX_EXCHANGE);
        // 指定死信routingKey
        args.put("x-dead-letter-routing-key", DLX_ROUTING_KEY);
        return QueueBuilder.durable(NORMAL_QUEUE).withArguments(args).build();
    }

    // 绑定普通队列和普通交换机
    @Bean
    public Binding normalBinding(Queue normalQueue, DirectExchange normalExchange) {
        return BindingBuilder.bind(normalQueue).to(normalExchange).with(NORMAL_ROUTING_KEY);
    }

    // ========== 死信部分 ==========
    @Bean
    public DirectExchange dlxExchange() {
        return ExchangeBuilder.directExchange(DLX_EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue dlxQueue() {
        return QueueBuilder.durable(DLX_QUEUE).build();
    }

    @Bean
    public Binding dlxBinding(Queue dlxQueue, DirectExchange dlxExchange) {
        return BindingBuilder.bind(dlxQueue).to(dlxExchange).with(DLX_ROUTING_KEY);
    }


    //==============下单付款通知===============
    @Bean
    public Queue orderNotifyQueue() {
        return QueueBuilder.durable(ORDER_NOTIFY_QUEUE).build();
    }

    @Bean
    public DirectExchange orderNotifyExchange() {
        return ExchangeBuilder.directExchange("order_notify_exchange").durable(true).build();
    }

    // 2. 声明 Binding（队列绑定到交换机，用 order_notify_rk 路由键）
    @Bean
    public Binding orderNotifyBinding(Queue orderNotifyQueue, DirectExchange orderNotifyExchange) {
        return BindingBuilder.bind(orderNotifyQueue).to(orderNotifyExchange).with(ORDER_NOTIFY_ROUTING_KEY);
    }


}
