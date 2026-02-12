package com.jiaoyi.product.util;

import com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;

/**
 * 商品域分片工具类
 * 提供统一的 product_shard_id 计算逻辑（基于 store_id）
 * 
 * 核心设计：
 * - 固定虚拟桶数：1024（2^10）
 * - product_shard_id = hash(store_id) & (1024 - 1) = hash(store_id) & 1023
 * - 后续扩容只需修改 product_shard_bucket_route 映射，无需修改此算法
 */
public class ProductShardUtil {
    
    /**
     * 固定虚拟桶数（2^10 = 1024）
     * 后续扩容时，此值保持不变，只需修改 product_shard_bucket_route 映射
     */
    public static final int BUCKET_COUNT = 1024;
    
    /**
     * 掩码（BUCKET_COUNT - 1 = 1023）
     * 用于位运算：hash & MASK 等价于 hash % BUCKET_COUNT，但性能更好
     */
    public static final int BUCKET_MASK = BUCKET_COUNT - 1;
    
    /**
     * 计算 product_shard_id（基于 store_id）
     * 
     * 算法：使用 Murmur3 哈希算法，保证分布均匀
     * product_shard_id = hash(store_id) & 1023
     * 
     * @param storeId 店铺ID（分片键）
     * @return product_shard_id（0-1023）
     */
    public static int calculateProductShardId(Long storeId) {
        if (storeId == null) {
            throw new IllegalArgumentException("storeId 不能为空");
        }
        return calculateProductShardId(String.valueOf(storeId));
    }
    
    /**
     * 计算 product_shard_id（基于 store_id，String 类型）
     * 
     * @param storeId 店铺ID（String 类型）
     * @return product_shard_id（0-1023）
     */
    public static int calculateProductShardId(String storeId) {
        if (storeId == null || storeId.isEmpty()) {
            throw new IllegalArgumentException("storeId 不能为空");
        }
        
        // 使用 Guava 的 Murmur3_32HashFunction（与 Guava 的 Hashing.murmur3_32() 一致）
        // 保证分布均匀，且结果稳定
        int hashCode = Hashing.murmur3_32().hashString(storeId, StandardCharsets.UTF_8).asInt();
        
        // 使用位运算取模（性能更好）
        // 注意：不使用 Math.abs()，因为 Math.abs(Integer.MIN_VALUE) 仍然是负数
        // 直接使用位与运算：hashCode & 1023 的结果必然在 0..1023 范围内（因为 1023 = 0x3FF）
        // 即使 hashCode 是负数，位与运算后也会得到正确的正数结果
        int productShardId = hashCode & BUCKET_MASK;
        
        return productShardId;
    }
    
    /**
     * 计算表索引（基于 product_shard_id）
     * 
     * 物理分表固定：4 张表（可配置，默认4）
     * table_idx = product_shard_id % 4
     * 
     * @param productShardId 分片ID（0-1023）
     * @param tableCountPerDb 每库表数量（默认4）
     * @return 表索引（0-3）
     */
    public static int calculateTableIndex(int productShardId, int tableCountPerDb) {
        return productShardId % tableCountPerDb;
    }
    
    /**
     * 计算表索引（使用默认表数量4）
     * 
     * @param productShardId 分片ID（0-1023）
     * @return 表索引（0-3）
     */
    public static int calculateTableIndex(int productShardId) {
        return calculateTableIndex(productShardId, 4);
    }
    
    /**
     * 格式化表索引为两位数字符串（用于表名）
     * 
     * 例如：0 -> "00", 1 -> "01", 3 -> "03"
     * 
     * @param tableIndex 表索引（0-3）
     * @return 两位数字符串
     */
    public static String formatTableIndex(int tableIndex) {
        return String.format("%02d", tableIndex);
    }
    
    /**
     * 验证 product_shard_id 是否有效
     * 
     * @param productShardId 分片ID
     * @return true 如果有效（0-1023），false 否则
     */
    public static boolean isValidProductShardId(int productShardId) {
        return productShardId >= 0 && productShardId < BUCKET_COUNT;
    }
}



