package com.jiaoyi.product.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiaoyi.product.entity.Outbox;
import com.jiaoyi.product.entity.OutboxNode;
import com.jiaoyi.product.mapper.primary.OutboxMapper;
import com.jiaoyi.product.mapper.primary.OutboxNodeMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Outbox服务
 * 负责写入outbox表和发送消息到RocketMQ
 */
@Service
@Slf4j
@org.springframework.context.annotation.DependsOn("databaseInitializer")
public class OutboxService {
    
    private final OutboxMapper outboxMapper;
    private final OutboxNodeMapper outboxNodeMapper;
    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;
    private final ThreadPoolExecutor nodeExecutor;
    private final RedissonClient redissonClient;
    
    // 使用构造函数注入，支持@Qualifier
    public OutboxService(
            OutboxMapper outboxMapper,
            OutboxNodeMapper outboxNodeMapper,
            RocketMQTemplate rocketMQTemplate,
            ObjectMapper objectMapper,
            @Qualifier("outboxNodeExecutor") ThreadPoolExecutor nodeExecutor,
            RedissonClient redissonClient) {
        this.outboxMapper = outboxMapper;
        this.outboxNodeMapper = outboxNodeMapper;
        this.rocketMQTemplate = rocketMQTemplate;
        this.objectMapper = objectMapper;
        this.nodeExecutor = nodeExecutor;
        this.redissonClient = redissonClient;
    }
    
    /**
     * 最大重试次数
     */
    private static final int MAX_RETRY_COUNT = 3;
    
    /**
     * 每次扫描的消息数量
     */
    private static final int BATCH_SIZE = 50;
    
    /**
     * 固定分片数量
     */
    @Value("${outbox.shard-count:10}")
    private int shardCount;
    
    /**
     * 定时任务执行间隔（毫秒）
     */
    @Value("${outbox.interval:2000}")
    private long interval;
    
    /**
     * 节点心跳超时时间（秒）
     */
    @Value("${outbox.node-registry.heartbeat-timeout:30}")
    private long heartbeatTimeout;
    
    /**
     * 节点心跳间隔（秒）
     */
    @Value("${outbox.node-registry.heartbeat-interval:10}")
    private long heartbeatInterval;
    
    /**
     * 服务器端口
     */
    @Value("${server.port:8080}")
    private int serverPort;
    
    /**
     * 本机IP地址（启动时初始化）
     */
    private String localIpAddress;
    
    /**
     * Redis中存储节点心跳的key前缀
     * 格式：outbox:node:heartbeat:{nodeId}
     * 值：心跳时间戳（毫秒）
     */
    private static final String NODE_HEARTBEAT_KEY_PREFIX = "outbox:node:heartbeat:";
    
    /**
     * 当前节点的OutboxNode记录
     */
    private OutboxNode currentOutboxNode;
    
    /**
     * 节点任务Future
     */
    private Future<?> nodeFuture;
    
    /**
     * 上次DB心跳时间（用于控制DB心跳频率）
     */
    private volatile long lastDbHeartbeatTime = 0;
    
    /**
     * 运行标志
     */
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    /**
     * 集群信息管理
     */
    private final com.jiaoyi.product.service.ClusterInfo clusterInfo = com.jiaoyi.product.service.ClusterInfo.getInstance();
    
    /**
     * 写入outbox表（在本地事务中调用）
     * 
     * @param topic RocketMQ Topic
     * @param tag RocketMQ Tag
     * @param messageKey 消息Key（可选）
     * @param message 消息对象（会被序列化为JSON）
     * @return Outbox记录
     */
    @Transactional
    public Outbox saveMessage(String topic, String tag, String messageKey, Object message) {
        try {
            String messageBody = objectMapper.writeValueAsString(message);
            
            // 先插入获取ID（shard_id先设为0，后续更新）
            Outbox outbox = Outbox.builder()
                    .shardId(0) // 临时值，插入后根据ID计算
                    .topic(topic)
                    .tag(tag)
                    .messageKey(messageKey)
                    .messageBody(messageBody)
                    .status(Outbox.OutboxStatus.PENDING)
                    .retryCount(0)
                    .createdAt(LocalDateTime.now())
                    .build();
            
            outboxMapper.insert(outbox);
            
            // 根据ID计算shard_id：id % shardCount
            int calculatedShardId = (int) (outbox.getId() % shardCount);
            outbox.setShardId(calculatedShardId);
            
            // 更新shard_id
            outboxMapper.updateShardId(outbox.getId(), calculatedShardId);
            
            log.info("已写入outbox表，ID: {}, ShardId: {}, Topic: {}, Tag: {}", 
                    outbox.getId(), calculatedShardId, topic, tag);
            
            return outbox;
        } catch (Exception e) {
            log.error("写入outbox表失败，Topic: {}, Tag: {}", topic, tag, e);
            throw new RuntimeException("写入outbox表失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 启动时初始化，启动一个节点
     */
    @PostConstruct
    public void init() {
        // 初始化本机IP地址
        try {
            this.localIpAddress = InetAddress.getLocalHost().getHostAddress();
            log.info("本机IP地址: {}", localIpAddress);
        } catch (Exception e) {
            log.error("获取本机IP地址失败，使用127.0.0.1", e);
            this.localIpAddress = "127.0.0.1";
        }
        
        log.info("========== 启动Outbox节点 ==========");
        log.info("本机IP: {}, 端口: {}", localIpAddress, serverPort);
        
        // 注册节点到数据库
        registerNode();
        
        running.set(true);
        
        // 使用线程池提交节点任务（一个应用一个节点）
        nodeFuture = nodeExecutor.submit(this::runNodeProcessor);
        
        log.info("========== 节点已启动 ==========");
    }
    
    /**
     * 注册节点到数据库
     */
    private void registerNode() {
        String nodeId = localIpAddress + ":" + serverPort;
        LocalDateTime expiredTime = LocalDateTime.now().plusSeconds(heartbeatTimeout);
        
        // 查询是否已存在
        OutboxNode existingNode = outboxNodeMapper.selectByNodeId(nodeId);
        
        if (existingNode == null) {
            // 新建节点
            currentOutboxNode = OutboxNode.builder()
                    .ip(localIpAddress)
                    .port(serverPort)
                    .nodeId(nodeId)
                    .enabled(1)
                    .expiredTime(expiredTime)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            outboxNodeMapper.insert(currentOutboxNode);
            log.info("节点注册成功，节点ID: {}，数据库ID: {}", nodeId, currentOutboxNode.getId());
        } else {
            // 更新节点信息
            currentOutboxNode = existingNode;
            outboxNodeMapper.updateByNodeId(nodeId, localIpAddress, serverPort, expiredTime);
            log.info("节点已存在，更新心跳，节点ID: {}，数据库ID: {}", nodeId, currentOutboxNode.getId());
        }
    }
    
    /**
     * 节点处理线程（一个应用一个节点）
     */
    private void runNodeProcessor() {
        if (currentOutboxNode == null) {
            log.error("节点未注册，无法启动处理线程");
            return;
        }
        
        String nodeId = currentOutboxNode.getNodeId();
        
        log.info("节点处理线程启动，节点ID: {}，数据库ID: {}", nodeId, currentOutboxNode.getId());
        
        try {
            // 心跳和处理循环
            while (running.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    // 发送心跳（Redis更频繁，DB更慢）
                    sendHeartbeat();
                    
                    // 更新节点索引和集群信息（从DB查询）
                    int myIndex = updateAndGetNodeIndex();
                    int totalNodes = getTotalNodes();
                    
                    if (myIndex == -1 || totalNodes == 0) {
                        log.warn("节点信息未初始化，跳过本次处理");
                        Thread.sleep(interval);
                        continue;
                    }
                    
                    // 更新集群信息（如果节点数或分片数发生变化）
                    if (clusterInfo.isChanged(shardCount, totalNodes)) {
                        clusterInfo.setShardCount(shardCount);
                        clusterInfo.setAvailableNodeCount(totalNodes);
                        clusterInfo.initShardPartitions();
                    }
                    
                    // 处理outbox消息
                    if (rocketMQTemplate != null) {
                        processOutboxMessagesForNode(myIndex);
                    }
                    
                    // 等待下次扫描
                    Thread.sleep(interval);
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("节点处理异常", e);
                    try {
                        Thread.sleep(interval);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            
            log.info("节点处理线程已停止");
            
        } catch (Exception e) {
            log.error("节点运行异常", e);
        }
    }
    
    /**
     * 发送心跳（Redis和DB分离频率）
     * 
     * Redis心跳：更频繁（每次循环都更新，用于快速检测）
     * DB心跳：更慢（按heartbeatInterval间隔更新，减少DB写压力）
     * 
     * 策略：
     * - Redis：每 interval（2秒）更新一次，快速检测节点存活
     * - DB：每 heartbeatInterval（10秒）更新一次，持久化存储
     */
    private void sendHeartbeat() {
        if (currentOutboxNode == null) {
            return;
        }
        
        long currentTime = System.currentTimeMillis();
        long heartbeatTimestamp = currentTime;
        
        // 1. 更新Redis心跳（每次循环都更新，快速检测）
        if (redissonClient != null) {
            try {
                String heartbeatKey = NODE_HEARTBEAT_KEY_PREFIX + currentOutboxNode.getNodeId();
                RBucket<Long> bucket = redissonClient.getBucket(heartbeatKey);
                bucket.set(heartbeatTimestamp);
                // Redis key自动过期，过期时间 = 心跳超时时间 + 5秒缓冲
                bucket.expire(heartbeatTimeout + 5, TimeUnit.SECONDS);
            } catch (Exception e) {
                // Redis不可用时，只记录日志，不影响主流程
                log.warn("更新Redis心跳失败，继续使用DB心跳: {}", e.getMessage());
            }
        }
        
        // 2. 更新数据库心跳（按heartbeatInterval间隔更新，减少DB写压力）
        long heartbeatIntervalMillis = heartbeatInterval * 1000L;
        if (currentTime - lastDbHeartbeatTime >= heartbeatIntervalMillis) {
            try {
                LocalDateTime expiredTime = LocalDateTime.now().plusSeconds(heartbeatTimeout);
                outboxNodeMapper.updateExpiredTime(currentOutboxNode.getId(), expiredTime);
                lastDbHeartbeatTime = currentTime;
            } catch (Exception e) {
                log.error("更新DB心跳失败", e);
            }
        }
    }
    
    /**
     * 更新并获取节点索引（DB + Redis混合查询）
     * 
     * 策略：
     * 1. 从DB查询所有启用的节点（按id desc排序，保证索引稳定性）
     * 2. 用Redis心跳过滤，只保留真正存活的节点
     * 3. 如果Redis不可用，降级到只用DB的expired_time判断
     */
    private int updateAndGetNodeIndex() {
        try {
            if (currentOutboxNode == null) {
                return -1;
            }
            
            // 从数据库查询所有启用的节点（按id desc排序）
            // 注意：这里查询所有启用的节点，不按expired_time过滤，因为要用Redis心跳来过滤
            // 使用 MySQL 支持的最小日期值（1000-01-01），而不是 LocalDateTime.MIN（MySQL不支持）
            List<OutboxNode> allEnabledNodes = outboxNodeMapper.selectAvailableNodes(
                LocalDateTime.of(1000, 1, 1, 0, 0));
            
            // 用Redis心跳过滤出真正存活的节点
            List<OutboxNode> aliveNodes = filterAliveNodesByRedis(allEnabledNodes);
            
            // 如果Redis不可用，降级到DB的expired_time判断
            if (aliveNodes.isEmpty() && redissonClient == null) {
                // Redis不可用，使用DB的expired_time判断
                LocalDateTime expiredTimeThreshold = LocalDateTime.now();
                aliveNodes = outboxNodeMapper.selectAvailableNodes(expiredTimeThreshold);
            }
            
            // 查找当前节点在列表中的位置（索引）
            for (int i = 0; i < aliveNodes.size(); i++) {
                if (aliveNodes.get(i).getId().equals(currentOutboxNode.getId())) {
                    return i;
                }
            }
            
            return -1;
        } catch (Exception e) {
            log.error("更新节点索引失败", e);
            return -1;
        }
    }
    
    /**
     * 获取总节点数（DB + Redis混合查询）
     */
    private int getTotalNodes() {
        try {
            // 从数据库查询所有启用的节点
            // 使用 MySQL 支持的最小日期值（1000-01-01），而不是 LocalDateTime.MIN（MySQL不支持）
            List<OutboxNode> allEnabledNodes = outboxNodeMapper.selectAvailableNodes(
                LocalDateTime.of(1000, 1, 1, 0, 0));
            
            // 用Redis心跳过滤出真正存活的节点
            List<OutboxNode> aliveNodes = filterAliveNodesByRedis(allEnabledNodes);
            
            // 如果Redis不可用，降级到DB的expired_time判断
            if (aliveNodes.isEmpty() && redissonClient == null) {
                LocalDateTime expiredTimeThreshold = LocalDateTime.now();
                aliveNodes = outboxNodeMapper.selectAvailableNodes(expiredTimeThreshold);
            }
            
            return aliveNodes.size();
        } catch (Exception e) {
            log.error("获取总节点数失败", e);
            return 0;
        }
    }
    
    /**
     * 用Redis心跳过滤出存活的节点
     * 
     * @param nodes 所有启用的节点列表
     * @return 存活的节点列表（保持原有顺序）
     */
    private List<OutboxNode> filterAliveNodesByRedis(List<OutboxNode> nodes) {
        if (redissonClient == null || nodes.isEmpty()) {
            return nodes;
        }
        
        List<OutboxNode> aliveNodes = new ArrayList<>();
        long currentTime = System.currentTimeMillis();
        long timeoutMillis = heartbeatTimeout * 1000L;
        
        for (OutboxNode node : nodes) {
            try {
                String heartbeatKey = NODE_HEARTBEAT_KEY_PREFIX + node.getNodeId();
                RBucket<Long> bucket = redissonClient.getBucket(heartbeatKey);
                Long heartbeatTimestamp = bucket.get();
                
                // 如果Redis中有心跳记录，且未过期，则认为节点存活
                if (heartbeatTimestamp != null && (currentTime - heartbeatTimestamp) < timeoutMillis) {
                    aliveNodes.add(node);
                }
            } catch (Exception e) {
                // Redis查询失败，降级：如果DB的expired_time有效，也认为节点存活
                if (node.getExpiredTime() != null && node.getExpiredTime().isAfter(LocalDateTime.now())) {
                    aliveNodes.add(node);
                }
            }
        }
        
        return aliveNodes;
    }
    
    /**
     * 清理过期节点（从DB删除）
     */
    @Scheduled(fixedDelayString = "${outbox.node-registry.cleanup-interval:60000}")
    public void cleanupExpiredNodes() {
        try {
            LocalDateTime expiredTimeThreshold = LocalDateTime.now();
            List<OutboxNode> expiredNodes = outboxNodeMapper.selectExpiredNodes(expiredTimeThreshold, 100);
            
            if (!expiredNodes.isEmpty()) {
                List<Long> ids = expiredNodes.stream().map(OutboxNode::getId).toList();
                outboxNodeMapper.deleteByIds(ids);
                log.info("清理过期节点: {} 个", expiredNodes.size());
            }
        } catch (Exception e) {
            log.error("清理过期节点失败", e);
        }
    }
    
    /**
     * 处理指定节点的outbox消息
     */
    private void processOutboxMessagesForNode(int nodeIndex) {
        try {
            // 获取当前节点负责的分片ID列表
            List<Integer> shardIds = clusterInfo.getShardIds(nodeIndex);
            
            if (shardIds.isEmpty()) {
                return;
            }
            
            // 查询属于当前节点分片的数据
            List<Outbox> pendingMessages = outboxMapper.selectPendingMessagesByShard(shardIds, BATCH_SIZE);
            
            if (pendingMessages.isEmpty()) {
                return;
            }
            
            log.info("节点{}扫描到 {} 条待发送的outbox消息，节点索引: {}, 负责分片: {}", 
                    nodeIndex, pendingMessages.size(), nodeIndex, shardIds);
            
            // 串行处理消息
            processMessagesSerially(pendingMessages);
            
        } catch (Exception e) {
            log.error("节点{}处理outbox消息异常", nodeIndex, e);
        }
    }
    
    /**
     * 串行处理消息
     */
    private void processMessagesSerially(List<Outbox> pendingMessages) {
        for (Outbox outbox : pendingMessages) {
            processSingleMessage(outbox);
        }
    }
    
    /**
     * 处理单条消息
     */
    private void processSingleMessage(Outbox outbox) {
        try {
            // 发送消息到RocketMQ
            sendMessageToRocketMQ(outbox);
            
            // 更新状态为已发送
            outboxMapper.updateStatusToSent(outbox.getId(), LocalDateTime.now());
            log.info("Outbox消息发送成功，ID: {}, Topic: {}, Tag: {}", 
                    outbox.getId(), outbox.getTopic(), outbox.getTag());
            
        } catch (Exception e) {
            log.error("Outbox消息发送失败，ID: {}, Topic: {}, Tag: {}", 
                    outbox.getId(), outbox.getTopic(), outbox.getTag(), e);
            
            // 增加重试次数
            outboxMapper.incrementRetryCount(outbox.getId());
            
            // 获取更新后的outbox记录
            Outbox updatedOutbox = outboxMapper.selectById(outbox.getId());
            
            // 如果超过最大重试次数，标记为失败
            if (updatedOutbox != null && updatedOutbox.getRetryCount() >= MAX_RETRY_COUNT) {
                outboxMapper.updateStatusToFailed(
                        updatedOutbox.getId(), 
                        "发送失败，已重试" + updatedOutbox.getRetryCount() + "次: " + e.getMessage(),
                        updatedOutbox.getRetryCount()
                );
                log.error("Outbox消息发送失败次数超过最大重试次数，标记为失败，ID: {}", updatedOutbox.getId());
            }
        }
    }
    
    /**
     * 发送消息到RocketMQ
     */
    private void sendMessageToRocketMQ(Outbox outbox) throws Exception {
        // 构建目标地址
        String destination = outbox.getTopic() + ":" + outbox.getTag();
        
        // 构建消息
        MessageBuilder<String> messageBuilder = MessageBuilder.withPayload(outbox.getMessageBody());
        
        // 如果有messageKey，设置到消息的KEYS header
        if (outbox.getMessageKey() != null && !outbox.getMessageKey().isEmpty()) {
            messageBuilder.setHeader("KEYS", outbox.getMessageKey());
        }
        
        Message<String> springMessage = messageBuilder.build();
        
        // 发送消息
        rocketMQTemplate.syncSend(destination, springMessage);
        
        log.debug("已发送消息到RocketMQ，Topic: {}, Tag: {}, MessageKey: {}", 
                outbox.getTopic(), outbox.getTag(), outbox.getMessageKey());
    }
    
    /**
     * 定时打印节点状态信息
     * 每30秒打印一次
     */
    @Scheduled(fixedDelayString = "${outbox.node-status-print-interval:30000}")
    public void printNodeStatus() {
        try {
            // 从数据库查询所有启用的节点
            // 使用 MySQL 支持的最小日期值（1000-01-01），而不是 LocalDateTime.MIN（MySQL不支持）
            List<OutboxNode> allEnabledNodes = outboxNodeMapper.selectAvailableNodes(
                LocalDateTime.of(1000, 1, 1, 0, 0));
            
            // 用Redis心跳过滤出存活的节点
            List<OutboxNode> aliveNodes = filterAliveNodesByRedis(allEnabledNodes);
            
            // 如果Redis不可用，降级到DB判断
            if (aliveNodes.isEmpty() && redissonClient == null) {
                LocalDateTime expiredTimeThreshold = LocalDateTime.now();
                aliveNodes = outboxNodeMapper.selectAvailableNodes(expiredTimeThreshold);
            }
            
            log.info("========== Outbox节点状态 ==========");
            log.info("总活跃节点数: {} (总启用节点数: {})", aliveNodes.size(), allEnabledNodes.size());
            for (int i = 0; i < aliveNodes.size(); i++) {
                OutboxNode node = aliveNodes.get(i);
                String heartbeatStatus = getHeartbeatStatus(node);
                String currentNodeMark = (currentOutboxNode != null && node.getId().equals(currentOutboxNode.getId())) ? " [当前节点]" : "";
                log.info("  节点索引 {}: {} (ID: {}, {}){}", 
                        i, node.getNodeId(), node.getId(), heartbeatStatus, currentNodeMark);
            }
            log.info("==================================");
        } catch (Exception e) {
            log.error("打印节点状态失败", e);
        }
    }
    
    /**
     * 获取节点心跳状态（用于日志显示）
     */
    private String getHeartbeatStatus(OutboxNode node) {
        if (redissonClient == null) {
            long secondsUntilExpire = java.time.Duration.between(LocalDateTime.now(), node.getExpiredTime()).getSeconds();
            return "DB心跳: " + secondsUntilExpire + "秒后过期";
        }
        
        try {
            String heartbeatKey = NODE_HEARTBEAT_KEY_PREFIX + node.getNodeId();
            RBucket<Long> bucket = redissonClient.getBucket(heartbeatKey);
            Long heartbeatTimestamp = bucket.get();
            
            if (heartbeatTimestamp != null) {
                long secondsAgo = (System.currentTimeMillis() - heartbeatTimestamp) / 1000;
                return "Redis心跳: " + secondsAgo + "秒前";
            } else {
                return "Redis心跳: 无";
            }
        } catch (Exception e) {
            long secondsUntilExpire = java.time.Duration.between(LocalDateTime.now(), node.getExpiredTime()).getSeconds();
            return "DB心跳: " + secondsUntilExpire + "秒后过期 (Redis不可用)";
        }
    }
    
    /**
     * 应用关闭时，停止节点
     */
    @PreDestroy
    public void destroy() {
        log.info("========== 停止节点 ==========");
        running.set(false);
        
        // 取消节点任务
        if (nodeFuture != null && !nodeFuture.isDone()) {
            nodeFuture.cancel(true);
        }
        
        // 关闭线程池
        nodeExecutor.shutdown();
        try {
            if (!nodeExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                nodeExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            nodeExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        // 清理Redis心跳
        if (redissonClient != null && currentOutboxNode != null) {
            try {
                String heartbeatKey = NODE_HEARTBEAT_KEY_PREFIX + currentOutboxNode.getNodeId();
                redissonClient.getBucket(heartbeatKey).delete();
                log.info("已清理Redis心跳");
            } catch (Exception e) {
                log.warn("清理Redis心跳失败: {}", e.getMessage());
            }
        }
        
        // 注意：节点记录保留在数据库中，通过expired_time判断是否存活
        // 如果需要完全删除，可以调用 outboxNodeMapper.deleteByIds()
        log.info("节点已停止（数据库记录保留，通过expired_time判断存活）");
    }
}

