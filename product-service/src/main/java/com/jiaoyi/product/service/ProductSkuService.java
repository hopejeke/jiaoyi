package com.jiaoyi.product.service;

import com.jiaoyi.product.entity.ProductSku;
import com.jiaoyi.product.entity.StoreProduct;
import com.jiaoyi.product.mapper.sharding.ProductSkuMapper;
import com.jiaoyi.product.mapper.sharding.StoreProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 商品SKU服务层
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductSkuService {
    
    private final ProductSkuMapper productSkuMapper;
    private final StoreProductMapper storeProductMapper;
    private final InventoryService inventoryService;
    
    /**
     * 创建SKU（自动创建库存记录）
     */
    @Transactional
    public ProductSku createSku(ProductSku sku) {
        log.info("创建SKU，商品ID: {}, SKU编码: {}", sku.getProductId(), sku.getSkuCode());
        
        // 检查SKU编码是否已存在（排除已删除的）
        Optional<ProductSku> existing = productSkuMapper.selectByProductIdAndSkuCode(sku.getProductId(), sku.getSkuCode());
        if (existing.isPresent() && (existing.get().getIsDelete() == null || !existing.get().getIsDelete())) {
            throw new RuntimeException("SKU编码已存在，商品ID: " + sku.getProductId() + ", SKU编码: " + sku.getSkuCode());
        }
        
        // 查询商品信息
        Optional<StoreProduct> product = storeProductMapper.selectById(sku.getProductId());
        if (!product.isPresent()) {
            throw new RuntimeException("商品不存在，商品ID: " + sku.getProductId());
        }
        StoreProduct storeProduct = product.get();
        
        // 设置默认值
        if (sku.getStatus() == null) {
            sku.setStatus(ProductSku.SkuStatus.ACTIVE);
        }
        if (sku.getIsDelete() == null) {
            sku.setIsDelete(false);
        }
        if (sku.getVersion() == null) {
            sku.setVersion(1L);
        }
        // 确保 sku_price 不为 null（如果未设置，使用商品价格）
        if (sku.getSkuPrice() == null) {
            if (storeProduct.getUnitPrice() != null) {
                sku.setSkuPrice(storeProduct.getUnitPrice());
            } else {
                sku.setSkuPrice(java.math.BigDecimal.ZERO);
            }
        }
        
        // 计算并设置 product_shard_id（基于 storeId，用于分库分表路由）
        if (sku.getProductShardId() == null) {
            int productShardId = com.jiaoyi.product.util.ProductShardUtil.calculateProductShardId(sku.getStoreId());
            sku.setProductShardId(productShardId);
        }
        
        // 插入SKU（insert 方法会通过 selectKey 自动设置 id）
        productSkuMapper.insert(sku);
        
        // 使用插入后返回的 ID 查询SKU（获取version）
        // 注意：insert 方法通过 selectKey 已经设置了 sku.getId()，所以可以直接使用
        ProductSku insertedSku = productSkuMapper.selectById(sku.getId())
                .orElseThrow(() -> new RuntimeException("SKU创建失败：插入后无法查询到SKU记录，ID: " + sku.getId()));
        
        log.info("SKU创建成功，SKU ID: {}, 版本号: {}", insertedSku.getId(), insertedSku.getVersion());
        
        // 自动创建SKU级别的库存记录
        inventoryService.createInventoryForSku(
            sku.getStoreId(),
            sku.getProductId(),
            insertedSku.getId(),
            storeProduct.getProductName(),
            sku.getSkuName()
        );
        log.info("SKU库存记录自动创建成功，SKU ID: {}", insertedSku.getId());
        
        return insertedSku;
    }
    
    /**
     * 根据ID查询SKU
     * 注意：由于 product_sku 表是分片表（基于 store_id），只传 id 可能查询不到
     * 建议使用 getSkuByProductIdAndId 或 getSkuByStoreIdAndId
     */
    public Optional<ProductSku> getSkuById(Long skuId) {
        Optional<ProductSku> sku = productSkuMapper.selectById(skuId);
        // 过滤已删除的SKU（因为 ShardingSphere 元数据问题，暂时不在 SQL 中过滤）
        if (sku.isPresent() && sku.get().getIsDelete() != null && sku.get().getIsDelete()) {
            return Optional.empty();
        }
        return sku;
    }
    
    /**
     * 根据商品ID和SKU ID查询SKU（推荐，包含分片键）
     */
    public Optional<ProductSku> getSkuByProductIdAndId(Long productId, Long skuId) {
        Optional<ProductSku> sku = productSkuMapper.selectByProductIdAndId(productId, skuId);
        // 过滤已删除的SKU
        if (sku.isPresent() && sku.get().getIsDelete() != null && sku.get().getIsDelete()) {
            return Optional.empty();
        }
        return sku;
    }
    
    /**
     * 根据店铺ID和SKU ID查询SKU（推荐，包含分片键）
     */
    public Optional<ProductSku> getSkuByStoreIdAndId(Long storeId, Long skuId) {
        Optional<ProductSku> sku = productSkuMapper.selectByStoreIdAndId(storeId, skuId);
        // 过滤已删除的SKU
        if (sku.isPresent() && sku.get().getIsDelete() != null && sku.get().getIsDelete()) {
            return Optional.empty();
        }
        return sku;
    }
    
    /**
     * 根据商品ID查询所有SKU
     */
    public List<ProductSku> getSkusByProductId(Long productId) {
        List<ProductSku> skus = productSkuMapper.selectByProductId(productId);
        // 在应用层过滤已删除的SKU（因为 ShardingSphere 元数据问题，暂时不在 SQL 中过滤）
        if (skus != null) {
            return skus.stream()
                    .filter(sku -> sku.getIsDelete() == null || !sku.getIsDelete())
                    .collect(java.util.stream.Collectors.toList());
        }
        return skus;
    }
    
    /**
     * 更新SKU
     */
    @Transactional
    public ProductSku updateSku(ProductSku sku) {
        log.info("更新SKU，SKU ID: {}", sku.getId());
        
        // 查询现有SKU获取版本号
        Optional<ProductSku> existing = productSkuMapper.selectById(sku.getId());
        if (!existing.isPresent()) {
            throw new RuntimeException("SKU不存在，SKU ID: " + sku.getId());
        }
        
        // 设置版本号用于乐观锁
        sku.setVersion(existing.get().getVersion());
        
        // 更新SKU
        int affectedRows = productSkuMapper.update(sku);
        if (affectedRows == 0) {
            throw new RuntimeException("SKU更新失败，可能版本号不匹配（乐观锁冲突）");
        }
        
        // 查询更新后的SKU
        ProductSku updatedSku = productSkuMapper.selectById(sku.getId())
                .orElseThrow(() -> new RuntimeException("SKU更新失败：更新后无法查询到SKU记录"));
        
        log.info("SKU更新成功，SKU ID: {}, 新版本号: {}", updatedSku.getId(), updatedSku.getVersion());
        
        return updatedSku;
    }
    
    /**
     * 删除SKU（逻辑删除）
     */
    @Transactional
    public void deleteSku(Long skuId) {
        log.info("删除SKU，SKU ID: {}", skuId);
        
        // 查询现有SKU获取版本号
        Optional<ProductSku> existing = productSkuMapper.selectById(skuId);
        if (!existing.isPresent()) {
            throw new RuntimeException("SKU不存在，SKU ID: " + skuId);
        }
        
        ProductSku sku = existing.get();
        sku.setIsDelete(true);
        sku.setVersion(existing.get().getVersion());
        
        // 逻辑删除SKU
        int affectedRows = productSkuMapper.deleteById(sku);
        if (affectedRows == 0) {
            throw new RuntimeException("SKU删除失败，可能版本号不匹配（乐观锁冲突）");
        }
        
        log.info("SKU删除成功，SKU ID: {}", skuId);
    }
}

