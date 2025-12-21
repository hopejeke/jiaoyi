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
 * 集群信息管理（Order Service）
 * 负责管理分片分配逻辑
 */
@Slf4j
@Data
@ToString
public class ClusterInfo {

    @Getter
    private static final ClusterInfo instance = new ClusterInfo();

    private int shardCount;
    private int availableNodeCount;
    private final Map<Integer, List<Integer>> nodeToShardsMap = new ConcurrentHashMap<>();

    private ClusterInfo() {
    }

    public boolean isChanged(int shardCount, int nodeCount) {
        if (this.shardCount != shardCount) {
            return true;
        }
        return this.availableNodeCount != nodeCount;
    }

    public void initShardPartitions() {
        if (this.availableNodeCount == 0) {
            log.warn("available node count is 0");
            return;
        }
        Map<Integer, List<Integer>> nodeToShardsMap = new HashMap<>();
        for (int i = 0; i < this.availableNodeCount; i++) {
            nodeToShardsMap.put(i, new ArrayList<>());
        }
        for (int i = 0; i < this.shardCount; i++) {
            int index = i % this.availableNodeCount;
            nodeToShardsMap.get(index).add(i);
        }
        this.nodeToShardsMap.clear();
        this.nodeToShardsMap.putAll(nodeToShardsMap);
        log.info("分片分配完成 - 分片数: {}, 节点数: {}, 分配结果: {}", 
                this.shardCount, this.availableNodeCount, this.nodeToShardsMap);
    }

    public List<Integer> getShardIds(int nodeIndex) {
        List<Integer> list = this.nodeToShardsMap.get(nodeIndex);
        if (list == null || list.isEmpty()) {
            log.warn("nodeIndex: {} has no any shard", nodeIndex);
            return new ArrayList<>();
        }
        return new ArrayList<>(list);
    }
}
