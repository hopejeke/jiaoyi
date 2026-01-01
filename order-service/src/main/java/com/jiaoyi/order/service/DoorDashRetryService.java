package com.jiaoyi.order.service;

import com.jiaoyi.order.entity.DoorDashRetryTask;
import com.jiaoyi.order.entity.Order;
import com.jiaoyi.order.mapper.DoorDashRetryTaskMapper;
import com.jiaoyi.order.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * DoorDash 重试服务
 * 负责自动重试 DoorDash 订单创建失败的任务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DoorDashRetryService {
    
    private final DoorDashRetryTaskMapper retryTaskMapper;
    private final OrderMapper orderMapper;
    private final ObjectProvider<PaymentService>  paymentServiceObjectProvider;
    private final RedissonClient redissonClient;
    
    @Value("${doordash.retry.max-count:3}")
    private int maxRetryCount;
    
    @Value("${doordash.retry.interval-minutes:5}")
    private int retryIntervalMinutes;
    
    @Value("${doordash.retry.enabled:true}")
    private boolean retryEnabled;
    
    /**
     * 创建重试任务（在支付成功但 DoorDash 创建失败时调用）
     */
    @Transactional
    public void createRetryTask(Long orderId, String merchantId, Long paymentId, Exception error) {
        try {
            // 检查是否已存在重试任务
            DoorDashRetryTask existingTask = retryTaskMapper.selectByMerchantIdAndOrderId(merchantId, orderId);
            if (existingTask != null) {
                log.warn("重试任务已存在，订单ID: {}, 任务ID: {}", orderId, existingTask.getId());
                return;
            }
            
            // 创建新的重试任务
            DoorDashRetryTask task = new DoorDashRetryTask();
            // ID 由 ShardingSphere 自动生成（雪花算法）
            task.setOrderId(orderId);
            task.setMerchantId(merchantId);
            task.setPaymentId(paymentId);
            task.setStatus(DoorDashRetryTask.TaskStatus.PENDING.getCode());
            task.setRetryCount(0);
            task.setMaxRetryCount(maxRetryCount);
            task.setErrorMessage(error != null ? error.getMessage() : "DoorDash 订单创建失败");
            task.setErrorStack(getStackTrace(error));
            task.setNextRetryTime(LocalDateTime.now().plusMinutes(retryIntervalMinutes));
            task.setCreateTime(LocalDateTime.now());
            task.setUpdateTime(LocalDateTime.now());
            
            retryTaskMapper.insert(task);
            
            // 注意：使用 useGeneratedKeys="true" 后，ShardingSphere 会自动生成 ID 并填充到 task.id 中
            // 如果 insert 后 id 仍为 null，说明 ShardingSphere 配置有问题，需要重新查询
            if (task.getId() == null) {
                log.warn("插入后任务ID仍为null，重新查询，订单ID: {}", orderId);
                DoorDashRetryTask insertedTask = retryTaskMapper.selectByMerchantIdAndOrderId(merchantId, orderId);
                if (insertedTask != null) {
                    task.setId(insertedTask.getId());
                } else {
                    log.error("插入重试任务失败，无法获取任务ID，订单ID: {}", orderId);
                    return;
                }
            }
            
            log.info("创建 DoorDash 重试任务，订单ID: {}, 任务ID: {}, 下次重试时间: {}", 
                    orderId, task.getId(), task.getNextRetryTime());
            
        } catch (Exception e) {
            log.error("创建 DoorDash 重试任务失败，订单ID: {}", orderId, e);
        }
    }
    
    /**
     * 定时任务：扫描待重试的任务并自动重试
     * 每5分钟执行一次
     */
    @Scheduled(fixedDelayString = "${doordash.retry.schedule-interval:300000}")
    public void retryPendingTasks() {
        if (!retryEnabled) {
            log.debug("DoorDash 重试任务已禁用");
            return;
        }
        
        log.info("开始扫描 DoorDash 重试任务");
        
        try {
            // 查询待重试的任务
            List<DoorDashRetryTask> pendingTasks = retryTaskMapper.selectPendingTasks(LocalDateTime.now());
            
            if (pendingTasks.isEmpty()) {
                log.debug("未发现待重试的任务");
                return;
            }
            
            log.info("发现 {} 个待重试的任务", pendingTasks.size());
            
            // 处理每个任务
            for (DoorDashRetryTask task : pendingTasks) {
                retryTask(task);
            }
            
        } catch (Exception e) {
            log.error("扫描 DoorDash 重试任务时发生异常", e);
        }
    }
    
    /**
     * 重试单个任务
     */
    @Transactional
    public void retryTask(DoorDashRetryTask task) {
        String lockKey = "doordash:retry:task:" + task.getId();
        RLock lock = redissonClient.getLock(lockKey);
        
        try {
            // 尝试获取锁，最多等待3秒，锁持有时间不超过60秒
            if (!lock.tryLock(3, 60, TimeUnit.SECONDS)) {
                log.warn("获取重试任务锁失败，任务ID: {}", task.getId());
                return;
            }
            
            // 重新查询任务状态，确保任务仍然是待重试状态
            DoorDashRetryTask currentTask = retryTaskMapper.selectById(task.getId());
            if (currentTask == null) {
                log.warn("任务不存在，任务ID: {}", task.getId());
                return;
            }
            
            if (!DoorDashRetryTask.TaskStatus.PENDING.getCode().equals(currentTask.getStatus())) {
                log.debug("任务状态已变更，跳过重试，任务ID: {}, 当前状态: {}", task.getId(), currentTask.getStatus());
                return;
            }
            
            // 更新任务状态为重试中
            retryTaskMapper.updateStatus(
                    task.getId(),
                    DoorDashRetryTask.TaskStatus.RETRYING.getCode(),
                    null,
                    null
            );
            
            log.info("开始重试 DoorDash 订单创建，任务ID: {}, 订单ID: {}, 重试次数: {}/{}", 
                    task.getId(), task.getOrderId(), task.getRetryCount() + 1, task.getMaxRetryCount());
            
            // 查询订单
            Order order = orderMapper.selectById(task.getOrderId());
            if (order == null) {
                log.error("订单不存在，任务ID: {}, 订单ID: {}", task.getId(), task.getOrderId());
                markTaskAsFailed(task, "订单不存在");
                return;
            }
            
            // 检查订单是否已经有配送ID（可能在其他地方已经创建成功）
            if (order.getDeliveryId() != null && !order.getDeliveryId().isEmpty()) {
                log.info("订单已有配送ID，标记任务为成功，任务ID: {}, 订单ID: {}, 配送ID: {}", 
                        task.getId(), task.getOrderId(), order.getDeliveryId());
                markTaskAsSuccess(task);
                return;
            }
            
            // 检查订单状态是否还允许创建 DoorDash 订单
            // 如果订单已取消、已退款等，不应该重试
            if (order.getStatus() != null) {
                Integer orderStatus = order.getStatus();
                // 订单已取消或已退款，不应该创建 DoorDash 订单
                if (orderStatus.equals(com.jiaoyi.order.enums.OrderStatusEnum.CANCELLED.getCode()) ||
                    orderStatus.equals(com.jiaoyi.order.enums.OrderStatusEnum.REFUNDED.getCode())) {
                    log.warn("订单状态不允许创建 DoorDash 订单，任务ID: {}, 订单ID: {}, 订单状态: {}",
                            task.getId(), task.getOrderId(), orderStatus);
                    markTaskAsFailed(task, "订单状态不允许创建 DoorDash 订单: " + orderStatus);
                    return;
                }
            }
            
            // 调用 PaymentService 创建 DoorDash 配送订单
            try {
                paymentServiceObjectProvider.getObject().createDoorDashDelivery(order);
                
                // 创建成功，标记任务为成功
                log.info("DoorDash 订单创建成功，任务ID: {}, 订单ID: {}", task.getId(), task.getOrderId());
                markTaskAsSuccess(task);
                
            } catch (Exception e) {
                log.error("重试 DoorDash 订单创建失败，任务ID: {}, 订单ID: {}", task.getId(), task.getOrderId(), e);
                
                // 更新重试信息
                int newRetryCount = task.getRetryCount() + 1;
                LocalDateTime nextRetryTime = null;
                String status = DoorDashRetryTask.TaskStatus.PENDING.getCode();
                
                if (newRetryCount >= task.getMaxRetryCount()) {
                    // 超过最大重试次数，标记为失败
                    status = DoorDashRetryTask.TaskStatus.FAILED.getCode();
                    log.warn("DoorDash 订单创建重试次数已达上限，任务ID: {}, 订单ID: {}, 重试次数: {}", 
                            task.getId(), task.getOrderId(), newRetryCount);
                } else {
                    // 计算下次重试时间（指数退避：第1次5分钟，第2次10分钟，第3次20分钟）
                    int delayMinutes = retryIntervalMinutes * (int) Math.pow(2, newRetryCount - 1);
                    nextRetryTime = LocalDateTime.now().plusMinutes(delayMinutes);
                }
                
                retryTaskMapper.updateRetryInfo(
                        task.getId(),
                        newRetryCount,
                        nextRetryTime,
                        LocalDateTime.now(),
                        e.getMessage(),
                        getStackTrace(e)
                );
                
                // 如果超过最大重试次数，更新状态为失败
                if (newRetryCount >= task.getMaxRetryCount()) {
                    retryTaskMapper.updateStatus(
                            task.getId(),
                            status,
                            e.getMessage(),
                            getStackTrace(e)
                    );
                }
            }
            
        } catch (InterruptedException e) {
            log.error("获取重试任务锁被中断，任务ID: {}", task.getId(), e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("重试任务处理异常，任务ID: {}", task.getId(), e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
    
    /**
     * 标记任务为成功
     */
    private void markTaskAsSuccess(DoorDashRetryTask task) {
        retryTaskMapper.updateSuccess(task.getId(), LocalDateTime.now());
    }
    
    /**
     * 标记任务为失败
     */
    private void markTaskAsFailed(DoorDashRetryTask task, String errorMessage) {
        retryTaskMapper.updateStatus(
                task.getId(),
                DoorDashRetryTask.TaskStatus.FAILED.getCode(),
                errorMessage,
                null
        );
    }
    
    /**
     * 获取异常堆栈（限制长度，避免数据库字段溢出）
     */
    private String getStackTrace(Exception e) {
        if (e == null) {
            return null;
        }
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        String stackTrace = sw.toString();
        // 限制错误堆栈长度为 5000 字符（TEXT 字段通常可以存储更多，但为了安全限制长度）
        int maxLength = 5000;
        if (stackTrace.length() > maxLength) {
            return stackTrace.substring(0, maxLength) + "... (truncated)";
        }
        return stackTrace;
    }
    
    /**
     * 手动触发重试（用于人工介入）
     */
    @Transactional
    public boolean manualRetry(Long taskId) {
        DoorDashRetryTask task = retryTaskMapper.selectById(taskId);
        if (task == null) {
            log.warn("任务不存在，任务ID: {}", taskId);
            return false;
        }
        
        // 重置重试次数和状态
        task.setRetryCount(0);
        task.setStatus(DoorDashRetryTask.TaskStatus.PENDING.getCode());
        task.setNextRetryTime(LocalDateTime.now());
        task.setErrorMessage(null);
        task.setErrorStack(null);
        task.setManualInterventionNote("人工触发重试");
        
        retryTaskMapper.updateRetryInfo(
                task.getId(),
                0,
                LocalDateTime.now(),
                null,
                null,
                null
        );
        retryTaskMapper.updateStatus(
                task.getId(),
                DoorDashRetryTask.TaskStatus.PENDING.getCode(),
                null,
                null
        );
        
        // 立即重试
        retryTask(task);
        
        return true;
    }
}

