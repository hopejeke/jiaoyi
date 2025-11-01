package com.jiaoyi.listener;

import com.jiaoyi.config.RocketMQConfig;
import com.jiaoyi.dto.ProductCacheUpdateMessage;
import com.jiaoyi.dto.ProductTransactionArg;
import com.jiaoyi.entity.Product;
import com.jiaoyi.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 商品缓存更新事务消息监听器
 * 用于处理RocketMQ事务消息的本地事务和回查
 * 
 * 注意：@RocketMQTransactionListener会自动注册事务监听器
 * 事务监听器会与sendMessageInTransaction中指定的producerGroup关联
 */
@Component
@RequiredArgsConstructor
@Slf4j
@RocketMQTransactionListener(
    rocketMQTemplateBeanName = "rocketMQTemplate",
    corePoolSize = 5, 
    maximumPoolSize = 10
)
public class ProductCacheTransactionListener implements RocketMQLocalTransactionListener {
    
    private final ProductMapper productMapper;
    
    /**
     * 执行本地事务
     * 
     * 【RocketMQ事务消息流程 - 关键点】
     * 步骤2: 半消息已发送到Broker后，立即调用此方法执行本地事务（数据库操作）
     * 
     * 【为什么在这里执行数据库操作】
     * - 先发送半消息，再执行数据库操作
     * - 如果数据库操作成功，提交半消息；如果失败，回滚半消息
     * - 这样即使宕机，如果半消息已发送但数据库操作未执行，半消息会被回滚，保证数据一致性
     * 
     * 此时半消息状态：
     * - 消息已存储到Broker，但对消费者不可见
     * - 等待本地事务执行结果来决定提交或回滚
     * 
     * @param msg 半消息内容（包含Product数据）
     * @param arg 发送消息时传递的参数（这里不使用）
     * @return 事务状态：
     *         - COMMIT: 提交半消息，消息变为可见，消费者可以消费
     *         - ROLLBACK: 回滚半消息，删除消息，不投递给消费者
     *         - UNKNOWN: 未知状态，触发回查机制
     */
    @Override
    @Transactional
    @SuppressWarnings("unchecked")
    public RocketMQLocalTransactionState executeLocalTransaction(Message msg, Object arg) {
        try {
            ProductCacheUpdateMessage message = (ProductCacheUpdateMessage) msg.getPayload();
            Product product = message.getProduct();
            Long productId = message.getProductId();
            
            // 从arg中获取ProductTransactionArg（如果存在）
            ProductTransactionArg transactionArg = null;
            if (arg != null && arg instanceof ProductTransactionArg) {
                transactionArg = (ProductTransactionArg) arg;
            }
            
            log.info("步骤2: 半消息已发送，开始执行本地事务（数据库操作），操作类型: {}, 商品ID: {}", 
                    message.getOperationType(), productId);
            
            // 根据操作类型执行数据库操作
            switch (message.getOperationType()) {
                case CREATE:
                    // CREATE操作：验证商品是否存在（数据库操作已在ProductService中完成）
                    // 注意：CREATE操作是折中方案：先INSERT获取ID，再发送半消息验证并更新缓存
                    if (productId == null) {
                        log.error("CREATE操作缺少productId，回滚半消息");
                        return RocketMQLocalTransactionState.ROLLBACK;
                    }
                    
                    log.info("CREATE操作：验证商品是否存在，商品ID: {}", productId);
                    Optional<Product> existingProductOpt = productMapper.selectById(productId);
                    if (!existingProductOpt.isPresent()) {
                        log.warn("CREATE操作：商品不存在，数据库INSERT可能失败，回滚半消息，商品ID: {}", productId);
                        return RocketMQLocalTransactionState.ROLLBACK;
                    }
                    
                    log.info("CREATE操作：商品验证成功，商品ID: {}", productId);
                    log.info("步骤3: 本地事务COMMIT，半消息变为正式消息，消费者可以消费");
                    return RocketMQLocalTransactionState.COMMIT;
                    
                case UPDATE:
                    // 执行数据库更新操作
                    if (product == null || productId == null) {
                        log.error("UPDATE操作缺少Product数据或productId，回滚半消息");
                        return RocketMQLocalTransactionState.ROLLBACK;
                    }
                    
                    log.info("执行数据库UPDATE操作，商品ID: {}", productId);
                    // 先查询商品是否存在
                    Optional<Product> existingProductOpt = productMapper.selectById(productId);
                    if (!existingProductOpt.isPresent()) {
                        log.warn("数据库UPDATE失败（未找到记录），回滚半消息，商品ID: {}", productId);
                        return RocketMQLocalTransactionState.ROLLBACK;
                    }
                    // 执行更新
                    productMapper.update(product);
                    log.info("数据库UPDATE成功，商品ID: {}", productId);
                    log.info("步骤3: 本地事务COMMIT，半消息变为正式消息，消费者可以消费");
                    return RocketMQLocalTransactionState.COMMIT;
                    
                case DELETE:
                    // 执行数据库删除操作
                    if (productId == null) {
                        log.error("DELETE操作缺少productId，回滚半消息");
                        return RocketMQLocalTransactionState.ROLLBACK;
                    }
                    
                    log.info("执行数据库DELETE操作，商品ID: {}", productId);
                    int deletedRows = productMapper.deleteById(productId);
                    if (deletedRows == 0) {
                        log.warn("数据库DELETE失败（未找到记录），回滚半消息，商品ID: {}", productId);
                        return RocketMQLocalTransactionState.ROLLBACK;
                    }
                    log.info("数据库DELETE成功，商品ID: {}", productId);
                    log.info("步骤3: 本地事务COMMIT，半消息变为正式消息，消费者可以消费");
                    return RocketMQLocalTransactionState.COMMIT;
                    
                case REFRESH:
                    // REFRESH操作不修改数据库，只验证商品是否存在
                    if (productId == null) {
                        log.error("REFRESH操作缺少productId，回滚半消息");
                        return RocketMQLocalTransactionState.ROLLBACK;
                    }
                    
                    Optional<Product> productOpt = productMapper.selectById(productId);
                    if (!productOpt.isPresent()) {
                        log.warn("REFRESH操作：商品不存在，回滚半消息，商品ID: {}", productId);
                        return RocketMQLocalTransactionState.ROLLBACK;
                    }
                    log.info("REFRESH操作：商品存在，提交半消息，商品ID: {}", productId);
                    log.info("步骤3: 本地事务COMMIT，半消息变为正式消息，消费者可以消费");
                    return RocketMQLocalTransactionState.COMMIT;
                    
                default:
                    log.error("未知的操作类型，回滚半消息: {}", message.getOperationType());
                    return RocketMQLocalTransactionState.ROLLBACK;
            }
            
        } catch (Exception e) {
            log.error("执行本地事务（数据库操作）失败，回滚半消息", e);
            return RocketMQLocalTransactionState.ROLLBACK;
        }
    }
    
    /**
     * 本地事务回查
     * 
     * 【RocketMQ事务消息回查机制】
     * 当executeLocalTransaction返回UNKNOWN，或者长时间未收到事务状态时，
     * RocketMQ会定期调用此方法回查本地事务状态
     * 
     * 回查场景：
     * - 网络异常导致事务状态未及时返回
     * - 应用重启导致事务状态丢失
     * - executeLocalTransaction返回UNKNOWN
     * 
     * @param msg 半消息内容
     * @return 事务状态：
     *         - COMMIT: 提交半消息，消息变为可见
     *         - ROLLBACK: 回滚半消息，删除消息
     *         - UNKNOWN: 继续回查（稍后再次调用此方法）
     */
    @Override
    @SuppressWarnings("unchecked")
    public RocketMQLocalTransactionState checkLocalTransaction(Message msg) {
        try {
            ProductCacheUpdateMessage message = (ProductCacheUpdateMessage) msg.getPayload();
            Long productId = message.getProductId();
            
            log.info("【回查机制】回查半消息的本地事务状态，商品ID: {}, 操作类型: {}", productId, message.getOperationType());
            
            // 检查商品是否存在
            Optional<Product> productOpt = productMapper.selectById(productId);
            
            // 根据操作类型判断事务状态
            if (message.getOperationType() == ProductCacheUpdateMessage.OperationType.DELETE) {
                // 删除操作：如果商品不存在，说明删除成功，提交事务
                return productOpt.isPresent() ? 
                    RocketMQLocalTransactionState.UNKNOWN : // 商品还存在，可能删除还未完成，继续回查
                    RocketMQLocalTransactionState.COMMIT;   // 商品不存在，删除成功，提交事务
            } else {
                // 其他操作：如果商品存在，说明操作成功，提交事务
                return productOpt.isPresent() ? 
                    RocketMQLocalTransactionState.COMMIT :   // 商品存在，操作成功，提交事务
                    RocketMQLocalTransactionState.ROLLBACK;  // 商品不存在，操作失败，回滚事务
            }
            
        } catch (Exception e) {
            log.error("回查本地事务状态失败", e);
            return RocketMQLocalTransactionState.UNKNOWN; // 发生异常，继续回查
        }
    }
}
