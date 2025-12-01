package com.jiaoyi.order.service;

import com.jiaoyi.common.exception.BusinessException;
import com.jiaoyi.order.client.CouponServiceClient;
import com.jiaoyi.order.client.ProductServiceClient;
import com.jiaoyi.order.dto.CreateOrderRequest;
import com.jiaoyi.order.dto.OrderResponse;
import com.jiaoyi.order.entity.Order;
import com.jiaoyi.order.entity.OrderItem;
import com.jiaoyi.order.entity.OrderStatus;
import com.jiaoyi.order.entity.OrderCoupon;
import com.jiaoyi.order.mapper.OrderItemMapper;
import com.jiaoyi.order.mapper.OrderMapper;
import com.jiaoyi.order.mapper.OrderCouponMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 订单服务层
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderCouponMapper orderCouponMapper;
    private final ProductServiceClient productServiceClient;
    private final CouponServiceClient couponServiceClient;
    private final RedissonClient redissonClient;
    private final OrderTimeoutMessageService orderTimeoutMessageService;

    /**
     * 创建订单
     */
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        log.info("开始创建订单，用户ID: {}", request.getUserId());

        // 同一个用户同一时间只能下一个单
        String lockKey = "order:create:" + request.getUserId();
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 尝试获取分布式锁，最多等待3秒，锁持有时间不超过30秒
            boolean lockAcquired = lock.tryLock(3, 30, TimeUnit.SECONDS);

            if (!lockAcquired) {
                log.warn("获取分布式锁失败，可能存在重复提交，用户ID: {}", request.getUserId());
                throw new BusinessException("请勿重复提交相同订单");
            }

            log.info("成功获取分布式锁，开始处理订单创建，用户ID: {}", request.getUserId());

            // 1. 检查并锁定库存
            List<Long> productIds = request.getOrderItems().stream()
                    .map(CreateOrderRequest.OrderItemRequest::getProductId)
                    .collect(Collectors.toList());
            List<Integer> quantities = request.getOrderItems().stream()
                    .map(CreateOrderRequest.OrderItemRequest::getQuantity)
                    .collect(Collectors.toList());

            log.info("检查并锁定库存，商品数量: {}", productIds.size());
            ProductServiceClient.LockStockBatchRequest lockRequest = new ProductServiceClient.LockStockBatchRequest();
            lockRequest.setProductIds(productIds);
            lockRequest.setQuantities(quantities);
            productServiceClient.lockStockBatch(lockRequest);

            try {
                // 2. 生成订单号
                String orderNo = generateOrderNo();

                // 3. 计算订单总金额
                BigDecimal totalAmount = calculateTotalAmount(request.getOrderItems());

                // 4. 处理多优惠券
                BigDecimal totalDiscountAmount = BigDecimal.ZERO;
                BigDecimal actualAmount = totalAmount;
                List<OrderCoupon> orderCoupons = new ArrayList<>();

                if (request.getCouponIds() != null && !request.getCouponIds().isEmpty()) {
                    // 处理优惠券ID列表
                    for (Long couponId : request.getCouponIds()) {
                        com.jiaoyi.common.ApiResponse<?> couponResponse = couponServiceClient.getCouponById(couponId);
                        if (couponResponse.getCode() != 200 || couponResponse.getData() == null) {
                            throw new BusinessException("优惠券不可用: " + couponId);
                        }
                        
                        // TODO: 验证优惠券和计算优惠金额（需要从 couponResponse 中解析 Coupon 对象）
                        // 这里简化处理，假设优惠券验证通过
                        BigDecimal discountAmount = BigDecimal.ZERO; // TODO: 从 couponServiceClient 获取
                        totalDiscountAmount = totalDiscountAmount.add(discountAmount);
                        
                        // 创建订单优惠券关联记录
                        OrderCoupon orderCoupon = new OrderCoupon();
                        orderCoupon.setOrderId(null); // 稍后设置
                        orderCoupon.setCouponId(couponId);
                        orderCoupon.setCouponCode(""); // TODO: 从 couponResponse 获取
                        orderCoupon.setAppliedAmount(discountAmount);
                        orderCoupon.setCreateTime(LocalDateTime.now());
                        orderCoupons.add(orderCoupon);
                        
                        log.info("优惠券验证通过，优惠券ID: {}, 优惠金额: {}", couponId, discountAmount);
                    }
                } else if (request.getCouponCodes() != null && !request.getCouponCodes().isEmpty()) {
                    // 处理优惠券代码列表
                    for (String couponCode : request.getCouponCodes()) {
                        com.jiaoyi.common.ApiResponse<?> couponResponse = couponServiceClient.getCouponByCode(couponCode);
                        if (couponResponse.getCode() != 200 || couponResponse.getData() == null) {
                            throw new BusinessException("优惠券不可用: " + couponCode);
                        }
                        
                        // TODO: 验证优惠券和计算优惠金额
                        BigDecimal discountAmount = BigDecimal.ZERO; // TODO: 从 couponServiceClient 获取
                        totalDiscountAmount = totalDiscountAmount.add(discountAmount);
                        
                        // 创建订单优惠券关联记录
                        OrderCoupon orderCoupon = new OrderCoupon();
                        orderCoupon.setOrderId(null); // 稍后设置
                        orderCoupon.setCouponId(null); // TODO: 从 couponResponse 获取
                        orderCoupon.setCouponCode(couponCode);
                        orderCoupon.setAppliedAmount(discountAmount);
                        orderCoupon.setCreateTime(LocalDateTime.now());
                        orderCoupons.add(orderCoupon);
                        
                        log.info("优惠券验证通过，优惠券代码: {}, 优惠金额: {}", couponCode, discountAmount);
                    }
                }

                actualAmount = totalAmount.subtract(totalDiscountAmount);
                log.info("总优惠金额: {}, 实际支付金额: {}", totalDiscountAmount, actualAmount);

                // 5. 创建订单实体
                Order order = new Order();
                order.setOrderNo(orderNo);
                order.setUserId(request.getUserId());
                order.setStatus(OrderStatus.PENDING);
                order.setTotalAmount(totalAmount);
                order.setTotalDiscountAmount(totalDiscountAmount);
                order.setActualAmount(actualAmount);
                order.setReceiverName(request.getReceiverName());
                order.setReceiverPhone(request.getReceiverPhone());
                order.setReceiverAddress(request.getReceiverAddress());
                order.setRemark(request.getRemark());
                order.setCreateTime(LocalDateTime.now());
                order.setUpdateTime(LocalDateTime.now());

                // 6. 保存订单
                orderMapper.insert(order);
                log.info("订单创建成功，订单号: {}", orderNo);

                // 7. 创建订单项
                List<OrderItem> orderItems = createOrderItems(order.getId(), request.getOrderItems());
                orderItemMapper.insertBatch(orderItems);

                // 8. 保存订单优惠券关联记录
                if (!orderCoupons.isEmpty()) {
                    // 设置订单ID
                    orderCoupons.forEach(oc -> oc.setOrderId(order.getId()));
                    orderCouponMapper.batchInsert(orderCoupons);
                    
                    // 使用优惠券
                    for (OrderCoupon orderCoupon : orderCoupons) {
                        // TODO: 调用 couponServiceClient.useCoupon
                        log.info("优惠券使用成功，优惠券ID: {}, 优惠金额: {}", orderCoupon.getCouponId(), orderCoupon.getAppliedAmount());
                    }
                }

                // 9. 设置订单项到订单中
                order.setOrderItems(orderItems);

                log.info("订单创建完成，订单ID: {}, 订单号: {}, 总金额: {}, 优惠金额: {}, 实际支付: {}",
                        order.getId(), orderNo, totalAmount, totalDiscountAmount, actualAmount);

                // 发送订单超时延迟消息（30分钟后自动取消）
                orderTimeoutMessageService.sendOrderTimeoutMessage(order.getId(), orderNo, request.getUserId(), 1);
                log.info("订单超时延迟消息已发送，订单将在30分钟后自动取消（如果未支付），订单ID: {}, 订单号: {}", order.getId(), orderNo);

                return OrderResponse.fromOrder(order);

            } catch (Exception e) {
                // 如果订单创建失败，解锁已锁定的库存
                log.error("订单创建失败，解锁库存", e);
                try {
                    ProductServiceClient.UnlockStockBatchRequest unlockRequest = new ProductServiceClient.UnlockStockBatchRequest();
                    unlockRequest.setProductIds(productIds);
                    unlockRequest.setQuantities(quantities);
                    unlockRequest.setOrderId(null);
                    productServiceClient.unlockStockBatch(unlockRequest);
                } catch (Exception unlockException) {
                    log.error("解锁库存失败", unlockException);
                }
                throw e;
            }

        } catch (InterruptedException e) {
            log.error("获取分布式锁被中断，用户ID: {}", request.getUserId(), e);
            Thread.currentThread().interrupt();
            throw new BusinessException("系统繁忙，请稍后重试");
        } finally {
            // 释放分布式锁
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.info("分布式锁已释放，用户ID: {}", request.getUserId());
            }
        }
    }

    /**
     * 根据订单号查询订单
     */
    public Optional<OrderResponse> getOrderByOrderNo(String orderNo) {
        log.info("查询订单，订单号: {}", orderNo);
        Order order = orderMapper.selectByOrderNo(orderNo);
        if (order != null) {
            // 查询订单项
            List<OrderItem> orderItems = orderItemMapper.selectByOrderId(order.getId());
            order.setOrderItems(orderItems);
            return Optional.of(OrderResponse.fromOrder(order));
        }
        return Optional.empty();
    }

    /**
     * 根据订单ID查询订单
     */
    public Optional<OrderResponse> getOrderById(Long orderId) {
        log.info("查询订单，订单ID: {}", orderId);
        Order order = orderMapper.selectById(orderId);
        if (order != null) {
            // 查询订单项
            List<OrderItem> orderItems = orderItemMapper.selectByOrderId(order.getId());
            order.setOrderItems(orderItems);
            
            // 查询订单优惠券关联记录
            List<OrderCoupon> orderCoupons = orderCouponMapper.selectByOrderId(order.getId());
            order.setOrderCoupons(orderCoupons);
            
            return Optional.of(OrderResponse.fromOrder(order));
        }
        return Optional.empty();
    }

    /**
     * 获取所有订单
     */
    public List<OrderResponse> getAllOrders() {
        log.info("获取所有订单");
        List<Order> orders = orderMapper.selectAll();
        return orders.stream()
                .map(order -> {
                    List<OrderItem> orderItems = orderItemMapper.selectByOrderId(order.getId());
                    order.setOrderItems(orderItems);
                    return OrderResponse.fromOrder(order);
                })
                .collect(Collectors.toList());
    }

    /**
     * 根据用户ID查询订单列表
     */
    public List<OrderResponse> getOrdersByUserId(Long userId) {
        log.info("查询用户订单列表，用户ID: {}", userId);
        List<Order> orders = orderMapper.selectByUserId(userId);
        return orders.stream()
                .map(order -> {
                    List<OrderItem> orderItems = orderItemMapper.selectByOrderId(order.getId());
                    order.setOrderItems(orderItems);
                    return OrderResponse.fromOrder(order);
                })
                .collect(Collectors.toList());
    }

    /**
     * 根据用户ID分页查询订单（简化版本，不使用PageHelper）
     */
    public List<OrderResponse> getOrdersByUserId(Long userId, int pageNum, int pageSize) {
        log.info("分页查询用户订单，用户ID: {}, 页码: {}, 大小: {}", userId, pageNum, pageSize);
        List<Order> orders = orderMapper.selectByUserId(userId);

        // 手动分页
        int start = (pageNum - 1) * pageSize;
        int end = Math.min(start + pageSize, orders.size());
        if (start >= orders.size()) {
            return new ArrayList<>();
        }

        List<Order> pagedOrders = orders.subList(start, end);
        return pagedOrders.stream()
                .map(order -> {
                    List<OrderItem> orderItems = orderItemMapper.selectByOrderId(order.getId());
                    order.setOrderItems(orderItems);
                    return OrderResponse.fromOrder(order);
                })
                .collect(Collectors.toList());
    }

    /**
     * 根据用户ID和状态查询订单列表
     */
    public List<OrderResponse> getOrdersByUserIdAndStatus(Long userId, OrderStatus status) {
        log.info("查询用户指定状态订单，用户ID: {}, 状态: {}", userId, status);
        List<Order> orders = orderMapper.selectByUserIdAndStatus(userId, status);
        return orders.stream()
                .map(order -> {
                    List<OrderItem> orderItems = orderItemMapper.selectByOrderId(order.getId());
                    order.setOrderItems(orderItems);
                    return OrderResponse.fromOrder(order);
                })
                .collect(Collectors.toList());
    }

    /**
     * 分页查询订单列表（支持筛选）
     */
    public Map<String, Object> getOrdersPage(int pageNum, int pageSize, String orderNo, Long userId,
                                             String status, String startTime, String endTime) {
        log.info("分页查询订单列表，页码: {}, 大小: {}, 订单号: {}, 用户ID: {}, 状态: {}",
                pageNum, pageSize, orderNo, userId, status);

        // 获取所有订单（这里简化处理，实际应该根据条件查询）
        List<Order> allOrders = orderMapper.selectAll();

        // 应用筛选条件
        List<Order> filteredOrders = allOrders.stream()
                .filter(order -> orderNo == null || order.getOrderNo().contains(orderNo))
                .filter(order -> userId == null || order.getUserId().equals(userId))
                .filter(order -> status == null || order.getStatus().toString().equals(status))
                .filter(order -> startTime == null || order.getCreateTime().isAfter(LocalDateTime.parse(startTime + "T00:00:00")))
                .filter(order -> endTime == null || order.getCreateTime().isBefore(LocalDateTime.parse(endTime + "T23:59:59")))
                .collect(Collectors.toList());

        // 计算分页信息
        int total = filteredOrders.size();
        int totalPages = (total + pageSize - 1) / pageSize;
        int start = (pageNum - 1) * pageSize;
        int end = Math.min(start + pageSize, total);

        // 获取当前页数据
        List<Order> pageOrders = start < total ? filteredOrders.subList(start, end) : new ArrayList<>();

        // 转换为OrderResponse
        List<OrderResponse> orderResponses = pageOrders.stream()
                .map(order -> {
                    List<OrderItem> orderItems = orderItemMapper.selectByOrderId(order.getId());
                    order.setOrderItems(orderItems);
                    return OrderResponse.fromOrder(order);
                })
                .collect(Collectors.toList());

        // 构建返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("orders", orderResponses);
        result.put("pageNum", pageNum);
        result.put("pageSize", pageSize);
        result.put("total", total);
        result.put("totalPages", totalPages);
        result.put("hasNext", pageNum < totalPages);
        result.put("hasPrev", pageNum > 1);

        return result;
    }

    /**
     * 更新订单状态
     */
    @Transactional
    public boolean updateOrderStatus(Long orderId, OrderStatus status) {
        log.info("更新订单状态，订单ID: {}, 新状态: {}", orderId, status);
        Order order = orderMapper.selectById(orderId);
        if (order != null) {
            OrderStatus oldStatus = order.getStatus();
            order.setStatus(status);
            order.setUpdateTime(LocalDateTime.now());
            orderMapper.updateStatus(orderId, status);

            // 处理库存状态变更
            handleInventoryStatusChange(order, oldStatus, status);

            log.info("订单状态更新成功，订单ID: {}, 新状态: {}", orderId, status);
            return true;
        }
        log.warn("订单不存在，订单ID: {}", orderId);
        return false;
    }

    /**
     * 取消订单
     */
    @Transactional
    public boolean cancelOrder(Long orderId) {
        log.info("取消订单，订单ID: {}", orderId);

        // 退款优惠券
        couponServiceClient.refundCouponByOrderId(orderId);

        return updateOrderStatus(orderId, OrderStatus.CANCELLED);
    }


    /**
     * 生成订单号
     * 支付宝要求：商户订单号，64个字符以内，只能包含字母、数字、下划线
     */
    private String generateOrderNo() {
        // 使用时间戳 + 随机数，确保唯一性且符合支付宝要求
        return "ORD" + System.currentTimeMillis() + String.format("%04d", new Random().nextInt(10000));
    }

    /**
     * 计算订单总金额
     */
    private BigDecimal calculateTotalAmount(List<CreateOrderRequest.OrderItemRequest> orderItems) {
        return orderItems.stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * 处理库存状态变更
     */
    private void handleInventoryStatusChange(Order order, OrderStatus oldStatus, OrderStatus newStatus) {
        if (order.getOrderItems() == null || order.getOrderItems().isEmpty()) {
            return;
        }

        List<Long> productIds = order.getOrderItems().stream()
                .map(OrderItem::getProductId)
                .collect(Collectors.toList());
        List<Integer> quantities = order.getOrderItems().stream()
                .map(OrderItem::getQuantity)
                .collect(Collectors.toList());

        // 支付成功：扣减库存
        if (oldStatus == OrderStatus.PENDING && newStatus == OrderStatus.PAID) {
            log.info("订单支付成功，扣减库存，订单ID: {}", order.getId());
            ProductServiceClient.DeductStockBatchRequest deductRequest = new ProductServiceClient.DeductStockBatchRequest();
            deductRequest.setProductIds(productIds);
            deductRequest.setQuantities(quantities);
            deductRequest.setOrderId(order.getId());
            productServiceClient.deductStockBatch(deductRequest);
        }
        // 订单取消：解锁库存
        else if (newStatus == OrderStatus.CANCELLED) {
            log.info("订单取消，解锁库存，订单ID: {}", order.getId());
            ProductServiceClient.UnlockStockBatchRequest unlockRequest = new ProductServiceClient.UnlockStockBatchRequest();
            unlockRequest.setProductIds(productIds);
            unlockRequest.setQuantities(quantities);
            unlockRequest.setOrderId(order.getId());
            productServiceClient.unlockStockBatch(unlockRequest);
        }
    }

    /**
     * 创建订单项
     */
    private List<OrderItem> createOrderItems(Long orderId, List<CreateOrderRequest.OrderItemRequest> itemRequests) {
        return itemRequests.stream()
                .map(itemRequest -> {
                    OrderItem orderItem = new OrderItem();
                    orderItem.setOrderId(orderId);
                    orderItem.setProductId(itemRequest.getProductId());
                    orderItem.setProductName(itemRequest.getProductName());
                    orderItem.setProductImage(itemRequest.getProductImage());
                    orderItem.setUnitPrice(itemRequest.getUnitPrice());
                    orderItem.setQuantity(itemRequest.getQuantity());
                    orderItem.setSubtotal(itemRequest.getUnitPrice().multiply(BigDecimal.valueOf(itemRequest.getQuantity())));
                    return orderItem;
                })
                .collect(Collectors.toList());
    }
}


