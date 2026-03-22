package com.sky.service.impl;

import com.sky.dto.GoodsSalesDTO;
import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.ReportService;
import com.sky.vo.OrderReportVO;
import com.sky.vo.SalesTop10ReportVO;
import com.sky.vo.TurnoverReportVO;
import com.sky.vo.UserReportVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ReportServiceImpl implements ReportService {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private UserMapper userMapper;

    /**
     * 统计营业额数据
     * @param begin
     * @param end
     * @return
     */
    @Override
    public TurnoverReportVO getTurnoverStatistics(LocalDate begin, LocalDate end) {
        //计算begin-end之间的日期
        List<LocalDate> dateList = new ArrayList<>();
        dateList.add(begin);
        while (!begin.equals(end)){
            begin = begin.plusDays(1);
            dateList.add(begin);
        }

        List<Double> sumList = new ArrayList<>();

        //获取这些日期对应的营业额数据 营业额是查询完成的订单
        //begin- end设置时分秒
        for (LocalDate date : dateList) {
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);//00:00:00
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);//23:59:59

            Map map = new HashMap();
            map.put("begin", beginTime);
            map.put("end", endTime);
            map.put("status", Orders.COMPLETED);

            Double sum = orderMapper.sumByMap(map);
            sum = sum == null ? 0.0 : sum;
            sumList.add(sum);
        }


        return TurnoverReportVO.builder()
                .dateList(StringUtils.join(dateList, ","))//转换为字符串带逗号
                .turnoverList(StringUtils.join(sumList, ","))
                .build();
    }

    /**
     * 统计用户数据
     * @param begin
     * @param end
     * @return
     */
    @Override
    public UserReportVO getUserStatistics(LocalDate begin, LocalDate end) {
        //计算begin-end之间的日期
        List<LocalDate> dateList = new ArrayList<>();
        dateList.add(begin);
        while (!begin.equals(end)){
            begin = begin.plusDays(1);
            dateList.add(begin);
        }

        List<Integer> newUserList = new ArrayList<>();
        List<Integer> totalUserList = new ArrayList<>();
        for (LocalDate date : dateList) {
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);//00:00:00
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);//23:59:59

            Map map = new HashMap();
            map.put("end", endTime);

            Integer totalUser = userMapper.countByMap(map);
            totalUserList.add(totalUser);
            map.put("begin", beginTime);
            Integer newUser = userMapper.countByMap(map);
            newUserList.add(newUser);
        }

        return UserReportVO.builder()
                .dateList(StringUtils.join(dateList, ","))
                .newUserList(StringUtils.join(newUserList, ","))
                .totalUserList(StringUtils.join(totalUserList, ","))
                .build();
    }

    /**
     * 统计订单数据
     * @param begin
     * @param end
     * @return
     */
    @Override
    public OrderReportVO getOrdersStatistics(LocalDate begin, LocalDate end) {
        //计算begin-end之间的日期
        List<LocalDate> dateList = new ArrayList<>();
        dateList.add(begin);
        while (!begin.equals(end)){
            begin = begin.plusDays(1);
            dateList.add(begin);
        }

        List<Integer> orderCountList = new ArrayList<>();
        List<Integer> validOrderCountList = new ArrayList<>();
        for (LocalDate date : dateList) {
            LocalDateTime beginTime = LocalDateTime.of(date, LocalTime.MIN);//00:00:00
            LocalDateTime endTime = LocalDateTime.of(date, LocalTime.MAX);//23:59:59

            Map map = new HashMap();
            map.put("end", endTime);
            map.put("begin", beginTime);

            //统计订单总数
            Integer totalOrder = orderMapper.countByMap(map);
            orderCountList.add(totalOrder);

            //统计有效订单数,订单状态为已完成
            map.put("status", Orders.COMPLETED);
            Integer validOrder = orderMapper.countByMap(map);
            validOrderCountList.add(validOrder);
        }
        Integer totalOrderCount = orderCountList.stream().reduce(Integer::sum).get();//reduce 是归约操作，用于将流中的元素“折叠”成一个值
        Integer validOrderCount = validOrderCountList.stream().reduce(Integer::sum).get();//Integer::sum是方法引用，怎样归约
        Double orderCompletionRate = 0.0;
        if (totalOrderCount != 0){
            orderCompletionRate = validOrderCount.doubleValue()/totalOrderCount;//包装类对象用.doubleValue()好
        }


        return OrderReportVO.builder()
                .dateList(StringUtils.join(dateList, ","))
                .orderCountList(StringUtils.join(orderCountList, ","))
                .validOrderCountList(StringUtils.join(validOrderCountList, ","))
                .validOrderCount(validOrderCount)
                .totalOrderCount(totalOrderCount)
                .orderCompletionRate(orderCompletionRate)
                .build();
    }

    /**
     * 统计指定时间区间内的销量排名
     * @param begin
     * @param end
     * @return
     */
    @Override
    public SalesTop10ReportVO getTop10(LocalDate begin, LocalDate end) {
        LocalDateTime beginTime = LocalDateTime.of(begin, LocalTime.MIN);//00:00:00
        LocalDateTime endTime = LocalDateTime.of(end, LocalTime.MAX);//23:59:59

        List<GoodsSalesDTO> salesTop10 = orderMapper.getSalesTop10(beginTime, endTime);
        //map 是映射操作，用于“一对一”转换。
        //GoodsSalesDTO::getName 表示调用每个对象的 getName() 方法。
        List<String> names = salesTop10.stream().map(GoodsSalesDTO::getName).collect(Collectors.toList());
        List<Integer> numbers = salesTop10.stream().map(GoodsSalesDTO::getNumber).collect(Collectors.toList());

        return SalesTop10ReportVO.builder()
                .nameList(StringUtils.join(names, ","))
                .numberList(StringUtils.join(numbers, ","))
                .build();
    }
}
