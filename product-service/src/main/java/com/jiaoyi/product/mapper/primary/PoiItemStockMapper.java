package com.jiaoyi.product.mapper.primary;

import com.jiaoyi.product.entity.PoiItemStock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品中心库存主表Mapper
 * 注意：此表存储在 primary 数据源，不分库分表
 */
@Mapper
public interface PoiItemStockMapper {
    
    /**
     * 根据品牌ID、门店ID、对象ID查询
     */
    PoiItemStock selectByBrandIdAndStoreIdAndObjectId(
        @Param("brandId") String brandId,
        @Param("storeId") String storeId,
        @Param("objectId") Long objectId
    );
    
    /**
     * 根据ID查询（加行锁 FOR UPDATE）
     */
    PoiItemStock selectByIdForUpdate(@Param("id") Long id);
    
    /**
     * 插入库存记录
     */
    int insert(PoiItemStock stock);
    
    /**
     * 更新库存（带乐观锁）
     */
    int updateStock(
        @Param("id") Long id,
        @Param("brandId") String brandId,
        @Param("storeId") String storeId,
        @Param("stockStatus") Integer stockStatus,
        @Param("stockType") Integer stockType,
        @Param("planQuantity") BigDecimal planQuantity,
        @Param("realQuantity") BigDecimal realQuantity,
        @Param("autoRestoreType") Integer autoRestoreType,
        @Param("autoRestoreAt") LocalDateTime autoRestoreAt,
        @Param("updatedAt") LocalDateTime updatedAt,
        @Param("oldUpdatedAt") LocalDateTime oldUpdatedAt
    );
    
    /**
     * 原子扣减库存（相对变更，用于订单扣减）
     * WHERE real_quantity >= delta 保证不扣成负数
     * 返回 affected rows：1=成功，0=库存不足
     */
    int atomicDeduct(
        @Param("id") Long id,
        @Param("delta") BigDecimal delta
    );
    
    /**
     * 原子扣减共享池库存
     * WHERE shared_pool_quantity >= delta 保证不扣成负数
     */
    int atomicDeductSharedPool(
        @Param("id") Long id,
        @Param("delta") BigDecimal delta
    );
    
    /**
     * 强制更新库存值（用于绝对设置冲突合并后的最终写入）
     * 同时更新 last_manual_set_time
     */
    int forceUpdateQuantity(
        @Param("id") Long id,
        @Param("realQuantity") BigDecimal realQuantity,
        @Param("lastManualSetTime") LocalDateTime lastManualSetTime,
        @Param("updatedAt") LocalDateTime updatedAt
    );
    
    /**
     * 更新库存状态（STATUS_CHANGE）
     * 售罄优先：如果新状态是售罄，无条件更新
     */
    int updateStockStatus(
        @Param("id") Long id,
        @Param("stockStatus") Integer stockStatus,
        @Param("updatedAt") LocalDateTime updatedAt
    );
    
    /**
     * 更新共享池库存
     */
    int updateSharedPoolQuantity(
        @Param("id") Long id,
        @Param("sharedPoolQuantity") BigDecimal sharedPoolQuantity
    );
    
    /**
     * 根据品牌ID、门店ID查询列表
     */
    List<PoiItemStock> selectByBrandIdAndStoreId(
        @Param("brandId") String brandId,
        @Param("storeId") String storeId
    );

    /**
     * 归还到共享池：增加 shared_pool_quantity 和 real_quantity（订单取消按源头还）
     */
    int atomicReturnToSharedPool(
        @Param("id") Long id,
        @Param("qty") BigDecimal qty
    );

    /**
     * 仅增加主表 real_quantity（还渠道时用，渠道表已 atomicDecreaseChannelSold）
     */
    int atomicIncreaseRealQuantity(
        @Param("id") Long id,
        @Param("qty") BigDecimal qty
    );

    /**
     * 带安全线底线的原子扣减（方案二 SAFETY_STOCK）：扣减后 real_quantity 不能低于 safetyFloor
     */
    int atomicDeductWithFloor(
        @Param("id") Long id,
        @Param("delta") BigDecimal delta,
        @Param("safetyFloor") BigDecimal safetyFloor
    );

    /**
     * 更新分配模式
     */
    int updateAllocationMode(
        @Param("id") Long id,
        @Param("allocationMode") String allocationMode
    );
}
