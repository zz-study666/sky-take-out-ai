package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.config.RabbitConfig;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.*;
import com.sky.entity.*;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.*;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.utils.HttpClientUtil;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import com.sky.websocket.WebSocketServer;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private AddressBookMapper addressBookMapper;
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private WeChatPayUtil weChatPayUtil;
    @Autowired
    private UserMapper userMapper;

    @Value("${sky.shop.address}")
    private String shopAddress;
    @Value("${sky.baidu.ak}")
    private String ak;

    @Autowired
    private WebSocketServer webSocketServer;

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;



    /**
     * 用户下单
     * @param ordersSubmitDTO
     * @return
     */
    @Transactional
    @Override
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {

        //处理业务异常，判断提交的地址簿和购物车中是否有数据
        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if (addressBook == null){
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }

        //判断用户地址是否可派送
        String address = addressBook.getProvinceName() + addressBook.getCityName() + addressBook.getDistrictName() + addressBook.getDetail();
        checkOutOfRange(address);

        //判断购物车数据
        ShoppingCart shoppingCart = new ShoppingCart();
        Long userId = BaseContext.getCurrentId();
        shoppingCart.setUserId(userId);
        List<ShoppingCart> shoppingCartlist = shoppingCartMapper.list(shoppingCart);
        if (shoppingCartlist == null || shoppingCartlist.size() == 0){
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        //订单表
        Orders orders = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO, orders);
        orders.setUserId(userId);
        orders.setStatus(Orders.PENDING_PAYMENT);
        orders.setPayStatus(Orders.UN_PAID);
        orders.setConsignee(addressBook.getConsignee());
        orders.setPhone(addressBook.getPhone());
        orders.setOrderTime(LocalDateTime.now());
        orders.setNumber(String.valueOf(System.currentTimeMillis()));

        //加入地址
        orders.setAddress(address);

        orderMapper.insert(orders);
        //订单明细表
        List<OrderDetail> orderDetailList = new ArrayList<>();
        for (ShoppingCart cart : shoppingCartlist) {
            OrderDetail orderDetail = new OrderDetail();
            BeanUtils.copyProperties(cart, orderDetail);
            orderDetail.setOrderId(orders.getId());
            orderDetailList.add(orderDetail);
        }
        orderDetailMapper.insertBatch(orderDetailList);

        //清空购物车
        shoppingCartMapper.deleteByUserId(userId);
        //返回结果
        OrderSubmitVO orderSubmitVO = OrderSubmitVO.builder().id(orders.getId()).orderTime(orders.getOrderTime()).orderNumber(orders.getNumber())
                .orderAmount(orders.getAmount()).build();

        //mq发送消息
        try {
            rabbitTemplate.convertAndSend(
                    RabbitConfig.NORMAL_EXCHANGE,
                    RabbitConfig.NORMAL_ROUTING_KEY,
                    orders.getId()
            );
        } catch (AmqpException e) {
            log.error("mq发送消息失败,未能进行超时订单处理", e);
        }

        return orderSubmitVO;
    }
    //计算客户地址的配送可行性
    private void checkOutOfRange(String address) {
        Map map = new HashMap();
        map.put("address", shopAddress);
        map.put("ak", ak);
        map.put("output", "json");

        //获取店铺的经纬度坐标
        String shopCoordinate = HttpClientUtil.doGet("https://api.map.baidu.com/geocoding/v3", map);
        //转Json 判断获取坐标数据是否成功
        JSONObject jsonObject = JSON.parseObject(shopCoordinate);
        String status = jsonObject.getString("status");
        if (!"0".equals(status)){
            throw new OrderBusinessException("店铺地址有误"+ status);
        }
        //数据解析
        JSONObject location = jsonObject.getJSONObject("result").getJSONObject("location");
        String lat = location.getString("lat");
        String lng = location.getString("lng");
        //店铺位置坐标
        String shopLngLat = lat + "," + lng;


        //获取用户地址经纬度坐标
        map.put("address", address);

        //获取用户的经纬度坐标
        String userCoordinate = HttpClientUtil.doGet("https://api.map.baidu.com/geocoding/v3", map);
        //转Json 判断获取坐标数据是否成功
        JSONObject jsonObject1 = JSON.parseObject(userCoordinate);
        status = jsonObject1.getString("status");
        if (!"0".equals(status)){
            throw new OrderBusinessException("派送地址有误"+ status);
        }
        //数据解析
        location = jsonObject1.getJSONObject("result").getJSONObject("location");
        lat = location.getString("lat");
        lng = location.getString("lng");
        //用户位置坐标
        String userLngLat = lat + "," + lng;

        //加入百度地图接口所需参数，获取路线规划
        map.put("origin",shopLngLat);
        map.put("destination",userLngLat);
        map.put("steps_info","0");

        //获取路线规划
        String json = HttpClientUtil.doGet("https://api.map.baidu.com/directionlite/v1/driving", map);
        //转Json 判断获取坐标数据是否成功
        JSONObject jsonObject2 = JSON.parseObject(json);
        status = jsonObject2.getString("status");
        if (!"0".equals(status)){
            String message = jsonObject2.getString("message");
            throw new OrderBusinessException("获取路线规划有误"+ status + message);
        }
        //数据解析，获取距离
        JSONObject jsonObject3 = jsonObject2.getJSONObject("result");
        JSONArray jsonArray = jsonObject3.getJSONArray("routes");
        Integer distance = (Integer)jsonArray.getJSONObject(0).get("distance");
        //判断距离是否超过5公里
        if (distance > 5000){
            throw new OrderBusinessException("超出配送范围");
        }
    }

    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return
     */
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        // 当前登录用户id
        Long userId = BaseContext.getCurrentId();
        User user = userMapper.getById(userId);

        //调用微信支付接口，生成预支付交易单
        JSONObject jsonObject = weChatPayUtil.pay(
                ordersPaymentDTO.getOrderNumber(), //商户订单号
                new BigDecimal(0.01), //支付金额，单位 元
                "苍穹外卖订单", //商品描述
                user.getOpenid() //微信用户的openid
        );

        if (jsonObject.getString("code") != null && jsonObject.getString("code").equals("ORDERPAID")) {
            throw new OrderBusinessException("该订单已支付");
        }

        OrderPaymentVO vo = jsonObject.toJavaObject(OrderPaymentVO.class);
        vo.setPackageStr(jsonObject.getString("package"));

        return vo;
    }

    /**
     * 支付成功，修改订单状态
     *
     * @param outTradeNo
     */
    @Transactional
    public void paySuccess(String outTradeNo) {

        // 根据订单号查询订单
        Orders ordersDB = orderMapper.getByNumber(outTradeNo);

        // 根据订单id更新订单的状态、支付方式、支付状态、结账时间
        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .checkoutTime(LocalDateTime.now())
                .build();

        orderMapper.update(orders);
        //再次检查订单商品是否存在限量菜品，检查库存是否足够，抛异常可回滚事务
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(ordersDB.getId());
        for (OrderDetail orderDetail : orderDetailList) {
            if (orderDetail.getDishId() != null) {
                //检查限量菜品库存是否足够
                seckill(orderDetail.getDishId(),orderDetail.getNumber());
            } else {
                //检查限量套餐库存是否足够
                seckill(orderDetail.getSetmealId(),orderDetail.getNumber());
            }
        }

        //---通过websocket发送消息给客户端 type orderId content
        //改为mq异步调用websocket发送消息给客户端
        Map map = new HashMap();
        map.put("type", 1);//1表示来单提醒 2表示催单提醒
        map.put("orderId", ordersDB.getId());
        map.put("content", "订单号："+ outTradeNo);
        String json = JSON.toJSONString(map);
        try {
            rabbitTemplate.convertAndSend(
                    RabbitConfig.ORDER_NOTIFY_EXCHANGE,RabbitConfig.ORDER_NOTIFY_ROUTING_KEY,json
            );
        } catch (AmqpException e) {
            log.info("mq发送消息失败,直接websocket发送,异常信息:{}", e);
            webSocketServer.sendToAllClient(json);
        }

        //webSocketServer.sendToAllClient(json);

    }
    //判断是否限量，检查库存是否足够，扣库存
    private void seckill(Long dishId,Integer dishNumber) {
        String stockKey = "seckill:stock:" + dishId;
        // 判断是不是限量菜品
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(stockKey))) {
            // 限量逻辑：加锁扣库存
            RLock lock = redissonClient.getLock("lock:seckill:" + dishId);
            try {
                boolean locked = lock.tryLock(3, 10, TimeUnit.SECONDS);
                if (!locked) {
                    throw new ShoppingCartBusinessException("抢购太火爆，请稍后再试");
                }
                int stock = Integer.parseInt(stringRedisTemplate.opsForValue().get(stockKey));
                //查询下单商品份数是否超过库存数量
                if (dishNumber > stock) {
                    throw new ShoppingCartBusinessException("库存不足");
                }
                if (stock <= 0) {
                    throw new ShoppingCartBusinessException("已售罄");
                }
                //支付才扣库存
                stringRedisTemplate.opsForValue().decrement(stockKey);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ShoppingCartBusinessException("系统异常");
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        }
    }

    /**
     * 历史订单查询
     *
     * @return
     */
    @Override
    public PageResult pageQuery(Integer pageNum, Integer pageSize, Integer status) {
        PageHelper.startPage(pageNum, pageSize);//设置分页参数
        OrdersPageQueryDTO ordersPageQueryDTO = new OrdersPageQueryDTO();
        ordersPageQueryDTO.setStatus(status);
        ordersPageQueryDTO.setUserId(BaseContext.getCurrentId());
        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);//分页条件查询
        List<OrderVO> list = new ArrayList<>();
        if (page != null && page.getTotal() > 0){
            for (Orders orders : page) {
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);
                List<OrderDetail> orderDetail = orderDetailMapper.getByOrderId(orders.getId());
                orderVO.setOrderDetailList(orderDetail);
                list.add(orderVO);
            }
        }

        return new PageResult(page.getTotal(), list);
    }

    /**
     * 订单详情
     */
    @Override
    public OrderVO getOrderDetail(Long id) {
        OrderVO orderVO = new OrderVO();
        Orders orders = orderMapper.getById(id);//查询订单数据
        BeanUtils.copyProperties(orders, orderVO);
        List<OrderDetail> orderDetail = orderDetailMapper.getByOrderId(orders.getId());//查询订单明细数据
        orderVO.setOrderDetailList(orderDetail);
        return orderVO;
    }

    /**
     * 用户取消订单
     * @param id
     */
    @Override
    public void cancel(Long id) {
        //查询订单数据,是否存在
        Orders ordersDB = orderMapper.getById(id);
        if (ordersDB == null){
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        //判断订单是否可取消  订单状态 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消
        if (ordersDB.getStatus() > 2){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        //订单可取消则修改订单状态，（退款现在处理不了）
        Orders orders = new Orders();
        orders.setId(ordersDB.getId());
        orders.setStatus(Orders.CANCELLED);
        orders.setCancelReason("用户取消");
        orders.setCancelTime(LocalDateTime.now());
        orderMapper.update(orders);

        if (ordersDB.getPayStatus() == Orders.PAID){
            List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(ordersDB.getId());
            for (OrderDetail orderDetail : orderDetailList) {
                if (orderDetail.getDishId() != null) {
                    //检查限量菜品库存是否存在并恢复库存
                    returnStock(orderDetail.getDishId());
                } else {
                    //检查限量套餐库存是否存在并恢复库存
                    returnStock(orderDetail.getSetmealId());
                }
            }
        }


    }

    private void returnStock(Long stockId) {
        String stockKey = "seckill:stock:" + stockId;
        // 先判断是不是秒杀商品（避免每次都抛异常）
        if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(stockKey))) {
            return;
        }
        stringRedisTemplate.opsForValue().increment(stockKey);
    }

    /**
     * 再来一单
     */
    @Override
    public void repetition(Long id) {
        //查询当前订单的详细信息
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(id);
        //Stream 流式处理，复制数据到新的集合中
        List<ShoppingCart> shoppingCartList = orderDetailList.stream().map(x -> {
            // 将原订单详情里面的菜品信息重新复制到购物车对象中
            ShoppingCart shoppingCart = new ShoppingCart();
            BeanUtils.copyProperties(x, shoppingCart);
            shoppingCart.setUserId(BaseContext.getCurrentId());
            shoppingCart.setCreateTime(LocalDateTime.now());
            return shoppingCart;
        }).collect(Collectors.toList());
        //插入购物车数据到数据库
        shoppingCartMapper.insertBatch(shoppingCartList);
    }

    /**
     * 搜索订单
     */
    @Override
    public PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO) {
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());
        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);
        List<OrderVO> orderVOList = getOrderVOList(page);//获取订单列表数据
        return new PageResult(page.getTotal(), orderVOList);
    }

    private List<OrderVO> getOrderVOList(Page<Orders> page) {
        List<OrderVO> orderVOList = new ArrayList<>();
        if (page != null && page.getTotal() > 0) {
            for (Orders orders : page) {
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);

                String orderDishes = getOrderDishesStr(orders.getId());//获取订单的所有菜品名称的拼接字符串
                orderVO.setOrderDishes(orderDishes);
                orderVOList.add(orderVO);
            }
        }
        return orderVOList;
    }

    private String getOrderDishesStr(Long orderId) {
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orderId);
        String orderDishes = orderDetailList.stream().map(x -> x.getName() + x.getNumber() + "份")
                .collect(Collectors.joining(","));
        return orderDishes;
    }

    /**
     * 各个状态的订单数量统计
     */
    @Override
    public OrderStatisticsVO statistics() {
        // 根据状态，分别查询出待接单、待派送、派送中的订单数量
        Integer toBeConfirmed =orderMapper.countStatus(Orders.TO_BE_CONFIRMED);
        Integer confirmed = orderMapper.countStatus(Orders.CONFIRMED);
        Integer deliveryInProgress = orderMapper.countStatus(Orders.DELIVERY_IN_PROGRESS);

        OrderStatisticsVO orderStatisticsVO = new OrderStatisticsVO();
        orderStatisticsVO.setToBeConfirmed(toBeConfirmed);
        orderStatisticsVO.setConfirmed(confirmed);
        orderStatisticsVO.setDeliveryInProgress(deliveryInProgress);
        return orderStatisticsVO;
    }

    /**
     * 接单
     */
    @Override
    public void confirm(OrdersConfirmDTO ordersConfirmDTO) {
        Orders orders = Orders.builder()
                .id(ordersConfirmDTO.getId())
                .status(Orders.CONFIRMED)
                .build();

        orderMapper.update(orders);
    }

    /**
     * 拒单
     */
    @Transactional
    @Override
    public void rejection(OrdersRejectionDTO ordersRejectionDTO) throws Exception {
        // 查询订单数据
        Long orderId = ordersRejectionDTO.getId();
        Orders ordersDB = orderMapper.getById(orderId);
        // 判断订单状态只有待接单状态才可拒单
        if (ordersDB == null || ! ordersDB.getStatus().equals(Orders.TO_BE_CONFIRMED)){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        // 更新订单状态为拒单 （退款）
        Integer payStatus = ordersDB.getPayStatus();
        if (payStatus.equals(Orders.PAID)){
            // 如果是已支付，需要退款
            log.info("申请退款");
        }

        Orders orders = Orders.builder()
                .id(ordersRejectionDTO.getId())
                .rejectionReason(ordersRejectionDTO.getRejectionReason())
                .status(Orders.CANCELLED)
                .cancelTime(LocalDateTime.now())
                .build();

        orderMapper.update(orders);
        // 检查订单详情是否存在并恢复库存
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(ordersDB.getId());
        for (OrderDetail orderDetail : orderDetailList) {
            if (orderDetail.getDishId() != null) {
                //检查限量菜品库存是否存在并恢复库存
                returnStock(orderDetail.getDishId());
            } else {
                //检查限量套餐库存是否存在并恢复库存
                returnStock(orderDetail.getSetmealId());
            }
        }
    }

    /**
     * 商家取消订单
     */
    @Override
    public void cancel(OrdersCancelDTO ordersCancelDTO) {
        // 查询订单数据
        Long orderId = ordersCancelDTO.getId();
        Orders ordersDB = orderMapper.getById(orderId);

        if (ordersDB == null){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        // 更新订单状态为取消订单 （退款）
        Integer payStatus = ordersDB.getPayStatus();
        // 如果是已支付，需要退款
        if (payStatus == Orders.PAID){
            log.info("申请退款");
        }
        Orders orders = Orders.builder()
                .id(ordersCancelDTO.getId())
                .cancelReason(ordersCancelDTO.getCancelReason())
                .status(Orders.CANCELLED)
                .cancelTime(LocalDateTime.now())
                .build();

        orderMapper.update(orders);
        // 检查订单详情是否存在并恢复库存
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(ordersDB.getId());
        for (OrderDetail orderDetail : orderDetailList) {
            if (orderDetail.getDishId() != null) {
                //检查限量菜品库存是否存在并恢复库存
                returnStock(orderDetail.getDishId());
            } else {
                //检查限量套餐库存是否存在并恢复库存
                returnStock(orderDetail.getSetmealId());
            }
        }

    }

    /**
     * 派送订单
     */
    @Override
    public void delivery(Long id) {
        // 查询订单数据
        Orders ordersDB = orderMapper.getById(id);
        // 判断订单状态只有待派送状态才可派送订单
        if (ordersDB == null || ! ordersDB.getStatus().equals(Orders.CONFIRMED)){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        // 更新订单状态为派送订单

        Orders orders = Orders.builder()
                .id(id)
                .status(Orders.DELIVERY_IN_PROGRESS)
                .cancelTime(LocalDateTime.now())
                .build();

        orderMapper.update(orders);
    }

    /**
     * 完成订单
     */
    @Override
    public void complete(Long id) {
        // 查询订单数据
        Orders ordersDB = orderMapper.getById(id);
        // 判断订单状态只有派送中状态才可完成订单
        if (ordersDB == null || ! ordersDB.getStatus().equals(Orders.DELIVERY_IN_PROGRESS)){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        // 更新订单状态为完成订单

        Orders orders = Orders.builder()
                .id(id)
                .status(Orders.COMPLETED)
                .cancelTime(LocalDateTime.now())
                .build();

        orderMapper.update(orders);
    }

    /**
     * 催单
     */
    @Override
    public void reminder(Long id) {
        // 查询订单数据
        Orders ordersDB = orderMapper.getById(id);
        // 判断订单状态只有派送中状态才可催单
        if (ordersDB == null){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        // 发送短信催单
        Map map = new HashMap();
        map.put("type",2);
        map.put("orderId",id);
        map.put("content","订单号："+ordersDB.getNumber());
        String json = JSON.toJSONString(map);
        webSocketServer.sendToAllClient(json);
    }
}
