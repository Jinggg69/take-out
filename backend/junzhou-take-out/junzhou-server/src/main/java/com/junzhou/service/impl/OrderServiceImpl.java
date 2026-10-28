package com.junzhou.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.junzhou.constant.MessageConstant;
import com.junzhou.context.BaseContext;
import com.junzhou.dto.OrdersCancelDTO;
import com.junzhou.dto.OrdersConfirmDTO;
import com.junzhou.dto.OrdersPageQueryDTO;
import com.junzhou.dto.OrdersPaymentDTO;
import com.junzhou.dto.OrdersRejectionDTO;
import com.junzhou.dto.OrdersSubmitDTO;
import com.junzhou.dto.ShoppingCartDTO;
import com.junzhou.entity.AddressBook;
import com.junzhou.entity.OrderDetail;
import com.junzhou.entity.Orders;
import com.junzhou.entity.ShoppingCart;
import com.junzhou.entity.User;
import com.junzhou.exception.AddressBookBusinessException;
import com.junzhou.exception.OrderBusinessException;
import com.junzhou.mapper.AddressBookMapper;
import com.junzhou.mapper.OrderDetailMapper;
import com.junzhou.mapper.OrderMapper;
import com.junzhou.mapper.ShoppingCartMapper;
import com.junzhou.mapper.UserMapper;
import com.junzhou.result.PageResult;
import com.junzhou.service.DeliveryRangeService;
import com.junzhou.service.OrderService;
import com.junzhou.service.OrderPricingService;
import com.junzhou.service.CartAccessService;
import com.junzhou.service.ShoppingCartService;
import com.junzhou.service.RefundTransactionService;
import com.junzhou.payment.OrderSubmissionIdentity;
import com.junzhou.utils.WeChatPayUtil;
import com.junzhou.vo.OrderPaymentVO;
import com.junzhou.vo.OrderStatisticsVO;
import com.junzhou.vo.OrderSubmitVO;
import com.junzhou.vo.OrderVO;
import com.junzhou.vo.OrderQuoteVO;
import com.junzhou.websocket.WebSocketServer;
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
import java.util.UUID;

/**
 * 订单业务实现类
 */
@Service
public class OrderServiceImpl implements OrderService {
    @Autowired private CartAccessService cartAccessService;
    @Autowired private ShoppingCartService shoppingCartService;
    @Autowired private RefundTransactionService refundTransactions;
    @Autowired
    private OrderPricingService orderPricingService;
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

    @Override
    public OrderQuoteVO quote() {
        return orderPricingService.quote();
    }

    /**
     * 用户下单
     * @param ordersSubmitDTO 订单提交信息
     * @return 订单提交结果
     */
    @Override
    @Transactional
    public OrderSubmitVO orderSubmit(OrdersSubmitDTO ordersSubmitDTO) {
        if (ordersSubmitDTO == null || ordersSubmitDTO.getAmount() == null
                || ordersSubmitDTO.getAmount().signum() < 0) {
            throw new OrderBusinessException("请确认有效的订单金额后重新提交");
        }
        if(ordersSubmitDTO.getPayMethod()!=1) throw new OrderBusinessException("当前仅支持微信支付");
        String fingerprint=OrderSubmissionIdentity.fingerprint(ordersSubmitDTO);
        //处理各种业务异常（地址簿为空，购物车数据为空）
        Long userId = cartAccessService.lockCurrentCart();
        if(ordersSubmitDTO.getClientRequestId()!=null) {
            Orders existing=orderMapper.getBySubmission(userId,ordersSubmitDTO.getClientRequestId());
            if(existing!=null) {
                if(!fingerprint.equals(existing.getSubmitFingerprint())) throw new OrderBusinessException("该请求标识已用于不同订单内容");
                return OrderSubmitVO.builder().id(existing.getId()).orderTime(existing.getOrderTime())
                        .orderNumber(existing.getNumber()).orderAmount(existing.getAmount()).build();
            }
        }
        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if (addressBook == null || userId == null || !userId.equals(addressBook.getUserId())) {
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }

        OrderQuoteVO quote = orderPricingService.quote();
        if (ordersSubmitDTO.getAmount().compareTo(quote.getAmount()) != 0) {
            throw new OrderBusinessException("商品价格或数量已变化，请重新确认订单金额");
        }

        String address = fullAddress(addressBook);
        deliveryRangeService.validate(address);

        //向订单表插入一条数据
        Orders order = new Orders();
        // Explicit client-owned fields only. Monetary values come exclusively from the server quotation.
        order.setAddressBookId(ordersSubmitDTO.getAddressBookId());
        order.setPayMethod(ordersSubmitDTO.getPayMethod());
        order.setRemark(ordersSubmitDTO.getRemark());
        order.setEstimatedDeliveryTime(ordersSubmitDTO.getEstimatedDeliveryTime());
        order.setDeliveryStatus(ordersSubmitDTO.getDeliveryStatus());
        order.setTablewareStatus(ordersSubmitDTO.getTablewareStatus());
        if (ordersSubmitDTO.getTablewareNumber() != null) order.setTablewareNumber(ordersSubmitDTO.getTablewareNumber());
        order.setAmount(quote.getAmount());
        order.setPackAmount(quote.getPackAmount());
        order.setOrderTime(LocalDateTime.now());
        order.setPayStatus(Orders.UN_PAID);
        order.setStatus(Orders.PENDING_PAYMENT);
        order.setNumber(UUID.randomUUID().toString().replace("-",""));
        order.setClientRequestId(ordersSubmitDTO.getClientRequestId());
        order.setSubmitFingerprint(fingerprint);
        order.setPhone(addressBook.getPhone());
        order.setAddress(address);
        order.setConsignee(addressBook.getConsignee());
        order.setUserId(userId);

        orderMapper.insert(order);
        //向订单明细表插入多条数据
        List<OrderDetail> orderDetailList = new ArrayList<>();
        for (ShoppingCart cart : quote.getItems()) {
            OrderDetail orderDetail = new OrderDetail();
            BeanUtils.copyProperties(cart, orderDetail);
            orderDetail.setId(null);
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
                "均州鱼庄外卖订单",
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
    @Transactional
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
        cartAccessService.lockCurrentCart();
        Orders order = requireOrder(id, false);
        requireUserOrder(order);
        List<OrderDetail> details = orderDetailMapper.getByOrderId(id);
        if (details.isEmpty()) {
            throw new OrderBusinessException("订单明细为空，无法再来一单");
        }
        for (OrderDetail detail : details) {
            if (detail.getNumber() == null || detail.getNumber() <= 0) throw new OrderBusinessException("历史订单商品数量异常");
            ShoppingCartDTO item = new ShoppingCartDTO();
            item.setDishId(detail.getDishId());
            item.setSetmealId(detail.getSetmealId());
            item.setDishFlavor(detail.getDishFlavor());
            shoppingCartService.add(item, detail.getNumber());
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
    @Transactional
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
    @Transactional
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
        boolean retry=Orders.CANCELLED.equals(order.getStatus());
        Orders update=Orders.builder().id(order.getId()).status(Orders.CANCELLED)
                .cancelReason(retry ? order.getCancelReason() : cancelReason)
                .rejectionReason(retry ? order.getRejectionReason() : rejectionReason)
                .cancelTime(retry ? order.getCancelTime() : LocalDateTime.now()).build();
        orderMapper.update(update);
        order.setStatus(Orders.CANCELLED);
        if(!Orders.UN_PAID.equals(order.getPayStatus())) refundTransactions.request(order);
    }

    private boolean isRefundPending(Orders order) {
        return Orders.CANCELLED.equals(order.getStatus());
    }

    @Override
    public PageResult refundHistory(int page,int pageSize) {
        if(BaseContext.getCurrentId()==null) throw new OrderBusinessException(MessageConstant.USER_NOT_LOGIN);
        OrdersPageQueryDTO query=new OrdersPageQueryDTO();
        query.setPage(page); query.setPageSize(pageSize); query.setUserId(BaseContext.getCurrentId()); query.setRefundOnly(true);
        return conditionSearch(query);
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
        orderMapper.update(Orders.builder().id(id).status(Orders.DELIVERY_IN_PROGRESS).dispatchTime(LocalDateTime.now()).build());
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
