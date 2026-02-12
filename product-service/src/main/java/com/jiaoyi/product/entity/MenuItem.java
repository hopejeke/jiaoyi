package com.jiaoyi.product.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 菜单项信息实体类
 * 对应 online-order-v2-backend 的 MenuInfo 模型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MenuItem {
    
    /**
     * 主键ID（雪花算法生成）
     */
    private Long id;
    
    /**
     * 餐馆ID（业务唯一键）
     */
    private String merchantId;
    
    /**
     * 分片ID（0-1023，与商品域统一分片）
     */
    private Integer productShardId;
    
    /**
     * 菜品ID（POS系统ID）
     */
    private Long itemId;
    
    /**
     * 图片信息（JSON：{"urls":[],"name":"","hisUrl":[]}）
     */
    private String imgInfo;
    
    /**
     * 版本号（用于乐观锁和缓存一致性）
     */
    private Long version;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}

