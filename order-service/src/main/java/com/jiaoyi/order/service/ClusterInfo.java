package com.jiaoyi.order.service;

import lombok.Data;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 集群信息管理
 * 负责管理分片分配逻辑
 */
@Slf4j
@Data
@ToString
public class ClusterInfo {

    @Getter
    private static final ClusterInfo instance = new ClusterInfo();

    /**
     * 固定分片数量
     */
    private int shardCount;

    /**
     * 可用节点数量
     */
    private int availableNodeCount;

    /**
     * 节点索引到分片ID列表的映射
     * key: 节点索引（0-based）
     * value: 该节点负责的分片ID列表
     */
    private final Map<Integer, List<Integer>> nodeToShardsMap = new ConcurrentHashMap<>();

    private ClusterInfo() {
    }

    /**
     * 分区或节点数与以前不一样，表示分区有变化
     *
     * @param shardCount 新的分区数
     * @param nodeCount  新的节点数
     * @return true:发生了变化
     */
    public boolean isChanged(int shardCount, int nodeCount) {
        if (this.shardCount != shardCount) {
            return true;
        }
        return this.availableNodeCount != nodeCount;
    }

    /**
     * 当节点总数或分区数发生变化时，需要重新计算各个节点掌控的分区
     * 使用取余算法：shardId % nodeCount = nodeIndex
     */
    public void initShardPartitions() {
        if (this.availableNodeCount == 0) {
            log.warn("available node count is 0");
            return;
        }
        Map<Integer, List<Integer>> nodeToShardsMap = new HashMap<>();
        // 初始化所有的节点，每个节点有个空集合，集合用来装载掌控的分区
        for (int i = 0; i < this.availableNodeCount; i++) {
            nodeToShardsMap.put(i, new ArrayList<>());
        }
        // 计算每个数据分区属于哪个节点
        for (int i = 0; i < this.shardCount; i++) {
            int index = i % this.availableNodeCount;
            nodeToShardsMap.get(index).add(i);
        }
        this.nodeToShardsMap.clear();
        this.nodeToShardsMap.putAll(nodeToShardsMap);
        log.info("分片分配完成 - 分片数: {}, 节点数: {}, 分配结果: {}", 
                this.shardCount, this.availableNodeCount, this.nodeToShardsMap);
    }

    /**
     * 获取指定节点负责的分片ID列表
     *
     * @param nodeIndex 节点索引（0-based）
     * @return 分片ID列表
     */
    public List<Integer> getShardIds(int nodeIndex) {
        List<Integer> list = this.nodeToShardsMap.get(nodeIndex);
        if (list == null || list.isEmpty()) {
            log.warn("nodeIndex: {} has no any shard", nodeIndex);
            return new ArrayList<>();
        }
        return new ArrayList<>(list);
    }
}
