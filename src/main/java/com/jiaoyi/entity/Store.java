package com.jiaoyi.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 店铺实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Store {
    
    private Long id;
    
    /**
     * 店铺名称
     */
    private String storeName;
    
    /**
     * 店铺编码
     */
    private String storeCode;
    
    /**
     * 店铺描述
     */
    private String description;
    
    /**
     * 店主姓名
     */
    private String ownerName;
    
    /**
     * 店主电话
     */
    private String ownerPhone;
    
    /**
     * 店铺地址
     */
    private String address;
    
    /**
     * 店铺状态：ACTIVE-营业中，INACTIVE-已关闭
     */
    private StoreStatus status;
    
    /**
     * 商品列表版本号（用于缓存一致性控制）
     * 当店铺的商品列表发生变化时（新增/删除商品），此版本号会递增
     */
    private Long productListVersion;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
    
    /**
     * 店铺状态枚举
     */
    public enum StoreStatus {
        ACTIVE("营业中"),
        INACTIVE("已关闭");
        
        private final String description;
        
        StoreStatus(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
}

