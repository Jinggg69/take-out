package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.dto.OrdersConfirmDTO;
import com.sky.dto.OrdersRejectionDTO;
import com.sky.dto.OrdersCancelDTO;
import com.sky.entity.AddressBook;
import com.sky.entity.OrderDetail;
import com.sky.entity.Orders;
import com.sky.entity.ShoppingCart;
import com.sky.entity.User;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.exception.RefundPendingException;
import com.sky.mapper.AddressBookMapper;
import com.sky.mapper.OrderDetailMapper;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.mapper.UserMapper;
import com.sky.service.OrderService;
import com.sky.service.DeliveryRangeService;
import com.sky.result.PageResult;
import com.sky.vo.OrderVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.websocket.WebSocketServer;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
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
    private UserMapper userMapper;
    @Autowired
    private WeChatPayUtil weChatPayUtil;
    @Autowired
    private DeliveryRangeService deliveryRangeService;
    @Autowired
    private WebSocketServer webSocketServer;
    /**
     * 用户端订单提交
     * @param ordersSubmitDTO 订单提交信息
     * @return 订单提交结果
     */
    @Override
    @Transactional
    public OrderSubmitVO orderSubmit(OrdersSubmitDTO ordersSubmitDTO) {
        //处理各种业务异常（地址簿为空，购物车数据为空）
        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        Long userId = BaseContext.getCurrentId();
        if (addressBook == null || userId == null || !userId.equals(addressBook.getUserId())) {
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }

        ShoppingCart shoppingCart = new ShoppingCart();
        shoppingCart.setUserId(userId);
        List<ShoppingCart> shoppingCartList = shoppingCartMapper.list(shoppingCart);
        if (shoppingCartList == null || shoppingCartList.size() == 0) {
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        String address = fullAddress(addressBook);
        deliveryRangeService.validate(address);

        //向订单表插入一条数据
        Orders order = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO, order);
        order.setOrderTime(LocalDateTime.now());
        order.setPayStatus(Orders.UN_PAID);
        order.setStatus(Orders.PENDING_PAYMENT);
        order.setNumber(String.valueOf(System.currentTimeMillis()));
        order.setPhone(addressBook.getPhone());
        order.setAddress(address);
        order.setConsignee(addressBook.getConsignee());
        order.setUserId(userId);

        orderMapper.insert(order);
        //向订单明细表插入多条数据
        List<OrderDetail> orderDetailList = new ArrayList<>();
        for (ShoppingCart cart : shoppingCartList) {
            OrderDetail orderDetail = new OrderDetail();
            BeanUtils.copyProperties(cart, orderDetail);
            orderDetail.setOrderId(order.getId());
            orderDetailList.add(orderDetail);
        }
        orderDetailMapper.insertBatch(orderDetailList);

        //清空当前用户购物车数据
        shoppingCartMapper.delete(userId);

        //封装VO对象返回结果
        OrderSubmitVO orderSubmitVO = OrderSubmitVO.builder()
                .id(order.getId())
                .orderTime(order.getOrderTime())
                .orderNumber(order.getNumber())
                .orderAmount(order.getAmount())
                .build();
        return orderSubmitVO;
    }

    /**
     * 订单支付。
     */
    @Override
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        Long userId = BaseContext.getCurrentId();
        Orders order = orderMapper.getByNumber(ordersPaymentDTO.getOrderNumber());
        requireUserOrder(order);
        requireStatus(order, Orders.PENDING_PAYMENT);
        if (!Orders.UN_PAID.equals(order.getPayStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        User user = userMapper.getById(userId);

        JSONObject jsonObject = weChatPayUtil.pay(
                ordersPaymentDTO.getOrderNumber(),
                order.getAmount(),
                "苍穹外卖订单",
                user.getOpenid()
        );

        if ("ORDERPAID".equals(jsonObject.getString("code"))) {
            throw new OrderBusinessException("该订单已支付");
        }

        OrderPaymentVO vo = jsonObject.toJavaObject(OrderPaymentVO.class);
        vo.setPackageStr(jsonObject.getString("package"));
        return vo;
    }

    /**
     * 支付成功，修改订单状态。
     */
    @Override
    @Transactional(noRollbackFor = RefundPendingException.class)
    public void paySuccess(String outTradeNo) {
        Orders order = orderMapper.getByNumberForUpdate(outTradeNo);
        if (order == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        if (Orders.CANCELLED.equals(order.getStatus()) && !Orders.REFUND.equals(order.getPayStatus())) {
            // 已取消订单的支付通知仍需记账和退款，不能直接忽略。
            Orders update = Orders.builder().id(order.getId()).checkoutTime(LocalDateTime.now()).build();
            try {
                requestRefund(order);
                update.setPayStatus(Orders.REFUND);
            } catch (RefundPendingException ex) {
                update.setPayStatus(Orders.PAID);
                orderMapper.update(update);
                throw ex;
            }
            orderMapper.update(update);
        } else {
            // 重复通知不能将后续业务状态改回待接单。
            orderMapper.markPaid(outTradeNo);
        }

        //通过websocket向客户端浏览器发送消息
        Map map = new HashMap<>();
        map.put("type", 1); //1表示来电提醒，2表示客户催单
        map.put("orderId", order.getId());
        map.put("content", "订单号:" + outTradeNo);
        String json = JSON.toJSONString(map);
        webSocketServer.sendToAllClient(json);
    }

    @Override
    public PageResult historyOrders(int page, int pageSize, Integer status) {
        if (BaseContext.getCurrentId() == null) {
            throw new OrderBusinessException(MessageConstant.USER_NOT_LOGIN);
        }
        OrdersPageQueryDTO query = new OrdersPageQueryDTO();
        query.setPage(page);
        query.setPageSize(pageSize);
        query.setStatus(status);
        query.setUserId(BaseContext.getCurrentId());
        return conditionSearch(query);
    }

    @Override
    public PageResult conditionSearch(OrdersPageQueryDTO query) {
        if (query.getPage() < 1 || query.getPageSize() < 1 || query.getPageSize() > 100
                || (query.getStatus() != null && (query.getStatus() < 1 || query.getStatus() > 6))
                || (query.getBeginTime() != null && query.getEndTime() != null
                && query.getBeginTime().isAfter(query.getEndTime()))) {
            throw new OrderBusinessException("订单查询参数错误");
        }
        try {
            PageHelper.startPage(query.getPage(), query.getPageSize());
            Page<Orders> orders = orderMapper.pageQuery(query);
            List<OrderVO> records = orders.stream().map(this::toOrderVO).collect(Collectors.toList());
            return new PageResult(orders.getTotal(), records);
        } finally {
            PageHelper.clearPage();
        }
    }

    @Override
    public OrderVO userOrderDetail(Long id) {
        Orders order = requireOrder(id, false);
        requireUserOrder(order);
        return toOrderVO(order);
    }

    @Override
    public OrderVO details(Long id) {
        return toOrderVO(requireOrder(id, false));
    }

    private OrderVO toOrderVO(Orders order) {
        OrderVO vo = new OrderVO();
        BeanUtils.copyProperties(order, vo);
        List<OrderDetail> details = orderDetailMapper.getByOrderId(order.getId());
        vo.setOrderDetailList(details);
        vo.setOrderDishes(details.stream().map(d -> d.getName() + "*" + d.getNumber())
                .collect(Collectors.joining(";")));
        return vo;
    }

    @Override
    public OrderStatisticsVO statistics() {
        OrderStatisticsVO vo = new OrderStatisticsVO();
        vo.setToBeConfirmed(orderMapper.countByStatus(Orders.TO_BE_CONFIRMED));
        vo.setConfirmed(orderMapper.countByStatus(Orders.CONFIRMED));
        vo.setDeliveryInProgress(orderMapper.countByStatus(Orders.DELIVERY_IN_PROGRESS));
        return vo;
    }

    @Override
    @Transactional(noRollbackFor = RefundPendingException.class)
    public void userCancelById(Long id) {
        Orders order = requireOrder(id, true);
        requireUserOrder(order);
        if (!isRefundPending(order)) {
            requireStatus(order, Orders.PENDING_PAYMENT, Orders.TO_BE_CONFIRMED);
        }
        cancelOrder(order, "用户取消", null);
    }

    @Override
    @Transactional
    public void repetition(Long id) {
        Orders order = requireOrder(id, false);
        requireUserOrder(order);
        List<OrderDetail> details = orderDetailMapper.getByOrderId(id);
        if (details.isEmpty()) {
            throw new OrderBusinessException("订单明细为空，无法再来一单");
        }
        for (OrderDetail detail : details) {
            ShoppingCart cart = new ShoppingCart();
            BeanUtils.copyProperties(detail, cart, "id");
            cart.setUserId(BaseContext.getCurrentId());
            cart.setCreateTime(LocalDateTime.now());
            shoppingCartMapper.insert(cart);
        }
    }

    @Override
    @Transactional
    public void confirm(OrdersConfirmDTO dto) {
        Orders order = requireOrder(dto.getId(), true);
        requireStatus(order, Orders.TO_BE_CONFIRMED);
        if (!Orders.PAID.equals(order.getPayStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        orderMapper.update(Orders.builder().id(order.getId()).status(Orders.CONFIRMED).build());
    }

    @Override
    @Transactional(noRollbackFor = RefundPendingException.class)
    public void rejection(OrdersRejectionDTO dto) {
        requireReason(dto.getRejectionReason());
        Orders order = requireOrder(dto.getId(), true);
        if (!isRefundPending(order)) {
            requireStatus(order, Orders.TO_BE_CONFIRMED);
        }
        cancelOrder(order, null, dto.getRejectionReason().trim());
    }

    @Override
    @Transactional(noRollbackFor = RefundPendingException.class)
    public void cancel(OrdersCancelDTO dto) {
        requireReason(dto.getCancelReason());
        Orders order = requireOrder(dto.getId(), true);
        if (!isRefundPending(order)) {
            requireStatus(order, Orders.PENDING_PAYMENT, Orders.TO_BE_CONFIRMED,
                    Orders.CONFIRMED, Orders.DELIVERY_IN_PROGRESS);
        }
        cancelOrder(order, dto.getCancelReason().trim(), null);
    }

    private void cancelOrder(Orders order, String cancelReason, String rejectionReason) {
        boolean retry = isRefundPending(order);
        Orders update = Orders.builder().id(order.getId()).status(Orders.CANCELLED)
                .cancelReason(retry ? order.getCancelReason() : cancelReason)
                .rejectionReason(retry ? order.getRejectionReason() : rejectionReason)
                .cancelTime(retry ? order.getCancelTime() : LocalDateTime.now()).build();
        if (Orders.PAID.equals(order.getPayStatus())) {
            try {
                requestRefund(order);
                update.setPayStatus(Orders.REFUND);
            } catch (RefundPendingException ex) {
                // 网络超时不代表退款未受理；提交取消意图，阻止后续接单派送。
                orderMapper.update(update);
                throw ex;
            }
        }
        orderMapper.update(update);
    }

    private boolean isRefundPending(Orders order) {
        return Orders.CANCELLED.equals(order.getStatus()) && Orders.PAID.equals(order.getPayStatus());
    }

    private void requestRefund(Orders order) {
        JSONObject response;
        try {
            // 固定退款单号，使结果不确定时的重试仍对应同一笔退款。
            response = JSONObject.parseObject(weChatPayUtil.refund(order.getNumber(),
                    order.getNumber(), order.getAmount(), order.getAmount()));
        } catch (Exception ex) {
            throw new RefundPendingException("订单已取消，退款结果待确认，请稍后重试取消退款");
        }
        String status = response == null ? null : response.getString("status");
        if ("SUCCESS".equals(status) || "PROCESSING".equals(status)) {
            return;
        }
        String code = response == null ? null : response.getString("code");
        if ("CLOSED".equals(status) || "ABNORMAL".equals(status)
                || (code != null && !code.trim().isEmpty())) {
            throw new OrderBusinessException("退款申请失败，请稍后重试");
        }
        throw new RefundPendingException("订单已取消，退款结果待确认，请稍后重试取消退款");
    }

    @Override
    @Transactional
    public void delivery(Long id) {
        Orders order = requireOrder(id, true);
        requireStatus(order, Orders.CONFIRMED);
        orderMapper.update(Orders.builder().id(id).status(Orders.DELIVERY_IN_PROGRESS).build());
    }

    @Override
    @Transactional
    public void complete(Long id) {
        Orders order = requireOrder(id, true);
        requireStatus(order, Orders.DELIVERY_IN_PROGRESS);
        orderMapper.update(Orders.builder().id(id).status(Orders.COMPLETED)
                .deliveryTime(LocalDateTime.now()).build());
    }

    private Orders requireOrder(Long id, boolean lock) {
        if (id == null || id <= 0) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        Orders order = lock ? orderMapper.getByIdForUpdate(id) : orderMapper.getById(id);
        if (order == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        return order;
    }

    private void requireUserOrder(Orders order) {
        if (order == null || BaseContext.getCurrentId() == null
                || !Objects.equals(BaseContext.getCurrentId(), order.getUserId())) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
    }

    private void requireStatus(Orders order, Integer... allowed) {
        for (Integer status : allowed) {
            if (status.equals(order.getStatus())) {
                return;
            }
        }
        throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
    }

    private void requireReason(String reason) {
        if (reason == null || reason.trim().isEmpty() || reason.length() > 255) {
            throw new OrderBusinessException("取消或拒单原因不能为空且不能超过255字");
        }
    }

    private String fullAddress(AddressBook addressBook) {
        StringBuilder address = new StringBuilder();
        for (String part : new String[]{addressBook.getProvinceName(), addressBook.getCityName(),
                addressBook.getDistrictName(), addressBook.getDetail()}) {
            if (part != null) {
                address.append(part);
            }
        }
        return address.toString();
    }

    public void reminder(Long id) {
        Orders order = orderMapper.getById(id);
        if (order == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Map map = new HashMap<>();
        map.put("type", 2); //1表示来电提醒，2表示客户催单
        map.put("orderId", id);
        map.put("content", "订单号:" + order.getNumber());
        String json = JSON.toJSONString(map);
        webSocketServer.sendToAllClient(json);
    }
}
