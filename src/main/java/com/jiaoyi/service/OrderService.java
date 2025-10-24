package com.jiaoyi.service;

import com.jiaoyi.dto.CreateOrderRequest;
import com.jiaoyi.dto.OrderResponse;
import com.jiaoyi.entity.Order;
import com.jiaoyi.entity.OrderItem;
import com.jiaoyi.entity.OrderStatus;
import com.jiaoyi.mapper.OrderMapper;
import com.jiaoyi.mapper.OrderItemMapper;
import com.jiaoyi.service.InventoryService;
import com.jiaoyi.util.CartFingerprintUtil;
// import com.github.pagehelper.PageHelper;
// import com.github.pagehelper.PageInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
    private final InventoryService inventoryService;
    private final RedissonClient redissonClient;
    
    /**
     * 创建订单
     */
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        log.info("开始创建订单，用户ID: {}", request.getUserId());
        
        // 生成购物车指纹作为分布式锁的key
        String lockKey = "order:create:" + CartFingerprintUtil.generateFingerprint(request);
        RLock lock = redissonClient.getLock(lockKey);
        
        try {
            // 尝试获取分布式锁，最多等待3秒，锁持有时间不超过30秒
            boolean lockAcquired = lock.tryLock(3, 30, TimeUnit.SECONDS);
            
            if (!lockAcquired) {
                log.warn("获取分布式锁失败，可能存在重复提交，用户ID: {}", request.getUserId());
                throw new RuntimeException("请勿重复提交相同订单");
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
            inventoryService.checkAndLockStockBatch(productIds, quantities);
            
            try {
                // 2. 生成订单号
                String orderNo = generateOrderNo();
                
                // 3. 计算订单总金额
                BigDecimal totalAmount = calculateTotalAmount(request.getOrderItems());
                
                // 4. 创建订单实体
                Order order = new Order();
                order.setOrderNo(orderNo);
                order.setUserId(request.getUserId());
                order.setStatus(OrderStatus.PENDING);
                order.setTotalAmount(totalAmount);
                order.setReceiverName(request.getReceiverName());
                order.setReceiverPhone(request.getReceiverPhone());
                order.setReceiverAddress(request.getReceiverAddress());
                order.setRemark(request.getRemark());
                order.setCreateTime(LocalDateTime.now());
                order.setUpdateTime(LocalDateTime.now());
                
                // 5. 保存订单
                orderMapper.insert(order);
                log.info("订单创建成功，订单号: {}", orderNo);
                
                // 6. 创建订单项
                List<OrderItem> orderItems = createOrderItems(order.getId(), request.getOrderItems());
                orderItemMapper.insertBatch(orderItems);
                
                // 7. 设置订单项到订单中
                order.setOrderItems(orderItems);
                
                log.info("订单创建完成，订单ID: {}, 订单号: {}", order.getId(), orderNo);
                return OrderResponse.fromOrder(order);
                
            } catch (Exception e) {
                // 如果订单创建失败，解锁已锁定的库存
                log.error("订单创建失败，解锁库存", e);
                try {
                    inventoryService.unlockStockBatch(productIds, quantities, null);
                } catch (Exception unlockException) {
                    log.error("解锁库存失败", unlockException);
                }
                throw e;
            }
            
        } catch (InterruptedException e) {
            log.error("获取分布式锁被中断，用户ID: {}", request.getUserId(), e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("系统繁忙，请稍后重试");
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
        return updateOrderStatus(orderId, OrderStatus.CANCELLED);
    }
    
    
    /**
     * 生成订单号
     */
    private String generateOrderNo() {
        return "ORD" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
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
            inventoryService.deductStockBatch(productIds, quantities, order.getId());
        }
        // 订单取消：解锁库存
        else if (newStatus == OrderStatus.CANCELLED) {
            log.info("订单取消，解锁库存，订单ID: {}", order.getId());
            inventoryService.unlockStockBatch(productIds, quantities, order.getId());
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
