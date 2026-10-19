package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.OrdersCancelDTO;
import com.sky.dto.OrdersConfirmDTO;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersRejectionDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.entity.AddressBook;
import com.sky.entity.OrderDetail;
import com.sky.entity.Orders;
import com.sky.entity.ShoppingCart;
import com.sky.entity.User;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.RefundPendingException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.AddressBookMapper;
import com.sky.mapper.OrderDetailMapper;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.mapper.UserMapper;
import com.sky.result.PageResult;
import com.sky.service.DeliveryRangeService;
import com.sky.service.OrderService;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import com.sky.websocket.WebSocketServer;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 订单业务实现类
 */
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
     * 用户下单
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
     * 订单支付，生成微信预支付交易单
     * @param ordersPaymentDTO 订单支付信息
     * @return 小程序调起支付所需的参数
     * @throws Exception 微信支付调用异常
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
     * 处理支付成功通知，更新支付状态或为已取消订单申请退款
     * @param outTradeNo 商户订单号
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
        Map<String, Object> map = new HashMap<>();
        map.put("type", 1); //1表示来电提醒，2表示客户催单
        map.put("orderId", order.getId());
        map.put("content", "订单号:" + outTradeNo);
        String json = JSON.toJSONString(map);
        webSocketServer.sendToAllClient(json);
    }

    /**
     * 分页查询当前用户的历史订单
     * @param page 页码
     * @param pageSize 每页记录数
     * @param status 订单状态，不传时查询全部状态
     * @return 历史订单分页结果
     */
    @Override
    public PageResult historyOrders(int page, int pageSize, Integer status) {
        if (BaseContext.getCurrentId() == null) {
            throw new OrderBusinessException(MessageConstant.USER_NOT_LOGIN);
        }
        OrdersPageQueryDTO ordersPageQueryDTO = new OrdersPageQueryDTO();
        ordersPageQueryDTO.setPage(page);
        ordersPageQueryDTO.setPageSize(pageSize);
        ordersPageQueryDTO.setStatus(status);
        ordersPageQueryDTO.setUserId(BaseContext.getCurrentId());
        return conditionSearch(ordersPageQueryDTO);
    }

    /**
     * 商家端订单条件分页查询
     * @param ordersPageQueryDTO 订单查询条件
     * @return 订单分页结果
     */
    @Override
    public PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO) {
        if (ordersPageQueryDTO.getPage() < 1
                || ordersPageQueryDTO.getPageSize() < 1 || ordersPageQueryDTO.getPageSize() > 100
                || (ordersPageQueryDTO.getStatus() != null
                && (ordersPageQueryDTO.getStatus() < 1 || ordersPageQueryDTO.getStatus() > 6))
                || (ordersPageQueryDTO.getBeginTime() != null && ordersPageQueryDTO.getEndTime() != null
                && ordersPageQueryDTO.getBeginTime().isAfter(ordersPageQueryDTO.getEndTime()))) {
            throw new OrderBusinessException("订单查询参数错误");
        }
        try {
            PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());
            Page<Orders> orders = orderMapper.pageQuery(ordersPageQueryDTO);
            //封装订单列表，补充每个订单的商品明细
            List<OrderVO> records = new ArrayList<>();
            for (Orders order : orders) {
                OrderVO orderVO = toOrderVO(order);
                records.add(orderVO);
            }
            return new PageResult(orders.getTotal(), records);
        } finally {
            PageHelper.clearPage();
        }
    }

    /**
     * 查询当前用户的订单详情
     * @param id 订单id
     * @return 订单和订单明细
     */
    @Override
    public OrderVO userOrderDetail(Long id) {
        Orders order = requireOrder(id, false);
        requireUserOrder(order);
        return toOrderVO(order);
    }

    /**
     * 商家查询订单详情
     * @param id 订单id
     * @return 订单和订单明细
     */
    @Override
    public OrderVO details(Long id) {
        return toOrderVO(requireOrder(id, false));
    }

    /**
     * 封装订单展示对象，补充订单明细和菜品名称
     * @param order 订单信息
     * @return 订单展示对象
     */
    private OrderVO toOrderVO(Orders order) {
        OrderVO vo = new OrderVO();
        BeanUtils.copyProperties(order, vo);
        List<OrderDetail> details = orderDetailMapper.getByOrderId(order.getId());
        vo.setOrderDetailList(details);
        //拼接订单菜品信息，例如：鱼香肉丝*2;米饭*1
        StringBuilder orderDishes = new StringBuilder();
        for (OrderDetail orderDetail : details) {
            if (orderDishes.length() > 0) {
                orderDishes.append(";");
            }
            orderDishes.append(orderDetail.getName()).append("*").append(orderDetail.getNumber());
        }
        vo.setOrderDishes(orderDishes.toString());
        return vo;
    }

    /**
     * 统计待接单、待派送和派送中的订单数量
     * @return 各状态订单数量
     */
    @Override
    public OrderStatisticsVO statistics() {
        OrderStatisticsVO vo = new OrderStatisticsVO();
        vo.setToBeConfirmed(orderMapper.countByStatus(Orders.TO_BE_CONFIRMED));
        vo.setConfirmed(orderMapper.countByStatus(Orders.CONFIRMED));
        vo.setDeliveryInProgress(orderMapper.countByStatus(Orders.DELIVERY_IN_PROGRESS));
        return vo;
    }

    /**
     * 用户取消订单，已支付订单同时申请退款
     * @param id 订单id
     */
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

    /**
     * 再来一单，将历史订单中的商品加入当前用户购物车
     * @param id 订单id
     */
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

    /**
     * 商家接单
     * @param ordersConfirmDTO 接单信息
     */
    @Override
    @Transactional
    public void confirm(OrdersConfirmDTO ordersConfirmDTO) {
        Orders order = requireOrder(ordersConfirmDTO.getId(), true);
        requireStatus(order, Orders.TO_BE_CONFIRMED);
        if (!Orders.PAID.equals(order.getPayStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        orderMapper.update(Orders.builder().id(order.getId()).status(Orders.CONFIRMED).build());
    }

    /**
     * 商家拒单，已支付订单同时申请退款
     * @param ordersRejectionDTO 拒单信息
     */
    @Override
    @Transactional(noRollbackFor = RefundPendingException.class)
    public void rejection(OrdersRejectionDTO ordersRejectionDTO) {
        requireReason(ordersRejectionDTO.getRejectionReason());
        Orders order = requireOrder(ordersRejectionDTO.getId(), true);
        if (!isRefundPending(order)) {
            requireStatus(order, Orders.TO_BE_CONFIRMED);
        }
        cancelOrder(order, null, ordersRejectionDTO.getRejectionReason().trim());
    }

    /**
     * 商家取消订单，已支付订单同时申请退款
     * @param ordersCancelDTO 订单取消信息
     */
    @Override
    @Transactional(noRollbackFor = RefundPendingException.class)
    public void cancel(OrdersCancelDTO ordersCancelDTO) {
        requireReason(ordersCancelDTO.getCancelReason());
        Orders order = requireOrder(ordersCancelDTO.getId(), true);
        if (!isRefundPending(order)) {
            requireStatus(order, Orders.PENDING_PAYMENT, Orders.TO_BE_CONFIRMED,
                    Orders.CONFIRMED, Orders.DELIVERY_IN_PROGRESS);
        }
        cancelOrder(order, ordersCancelDTO.getCancelReason().trim(), null);
    }

    /**
     * 处理订单取消和退款，重试时保留首次取消原因及时间
     * @param order 原订单信息
     * @param cancelReason 取消原因
     * @param rejectionReason 拒单原因
     */
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

    /**
     * 判断订单是否已取消但退款结果仍待确认
     * @param order 订单信息
     * @return 是否需要继续确认退款结果
     */
    private boolean isRefundPending(Orders order) {
        return Orders.CANCELLED.equals(order.getStatus()) && Orders.PAID.equals(order.getPayStatus());
    }

    /**
     * 使用固定退款单号申请全额退款，区分受理成功、明确失败和结果待确认
     * @param order 订单信息
     */
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

    /**
     * 派送订单
     * @param id 订单id
     */
    @Override
    @Transactional
    public void delivery(Long id) {
        Orders order = requireOrder(id, true);
        requireStatus(order, Orders.CONFIRMED);
        orderMapper.update(Orders.builder().id(id).status(Orders.DELIVERY_IN_PROGRESS).build());
    }

    /**
     * 完成订单，记录实际送达时间
     * @param id 订单id
     */
    @Override
    @Transactional
    public void complete(Long id) {
        Orders order = requireOrder(id, true);
        requireStatus(order, Orders.DELIVERY_IN_PROGRESS);
        orderMapper.update(Orders.builder().id(id).status(Orders.COMPLETED)
                .deliveryTime(LocalDateTime.now()).build());
    }

    /**
     * 查询订单并校验是否存在，状态变更时对订单记录加锁
     * @param id 订单id
     * @param lock 是否对查询结果加行锁
     * @return 订单信息
     */
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

    /**
     * 校验订单是否属于当前登录用户
     * @param order 待校验的订单
     */
    private void requireUserOrder(Orders order) {
        if (order == null || BaseContext.getCurrentId() == null
                || !Objects.equals(BaseContext.getCurrentId(), order.getUserId())) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
    }

    /**
     * 校验订单是否处于允许操作的状态
     * @param order 订单信息
     * @param allowed 允许操作的订单状态
     */
    private void requireStatus(Orders order, Integer... allowed) {
        for (Integer status : allowed) {
            if (status.equals(order.getStatus())) {
                return;
            }
        }
        throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
    }

    /**
     * 校验取消或拒单原因不能为空且不超过255字
     * @param reason 取消或拒单原因
     */
    private void requireReason(String reason) {
        if (reason == null || reason.trim().isEmpty() || reason.length() > 255) {
            throw new OrderBusinessException("取消或拒单原因不能为空且不能超过255字");
        }
    }

    /**
     * 拼接省、市、区和详细地址，生成完整收货地址
     * @param addressBook 地址簿信息
     * @return 完整收货地址
     */
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

    /**
     * 用户催单，向商家发送WebSocket消息
     * @param id 订单id
     */
    @Override
    public void reminder(Long id) {
        Orders order = orderMapper.getById(id);
        if (order == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Map<String, Object> map = new HashMap<>();
        map.put("type", 2); //1表示来电提醒，2表示客户催单
        map.put("orderId", id);
        map.put("content", "订单号:" + order.getNumber());
        String json = JSON.toJSONString(map);
        webSocketServer.sendToAllClient(json);
    }
}
