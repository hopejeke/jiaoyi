# ShardingSphere 元数据缓存问题修复

## 问题描述

即使数据库表中已经有 `is_delete` 字段，ShardingSphere 仍然报错：`Unknown column 'is_delete' in 'where clause'`

## 原因

ShardingSphere 在启动时会加载表结构元数据并缓存。如果表结构在应用启动后发生变化（比如手动执行 ALTER TABLE），ShardingSphere 的元数据缓存不会自动更新，导致解析 SQL 时仍然使用旧的表结构。

## 解决方案

### 方案1：重启应用（推荐）

**最简单有效的方法**：重启 product-service 应用，ShardingSphere 会重新加载表结构元数据。

### 方案2：禁用元数据校验（已配置）

已在 `ShardingSphereConfig.java` 中添加配置：
```java
props.setProperty("check-table-metadata-enabled", "false");
```

这会禁用表结构元数据校验，允许表结构动态变化。**重启应用后生效**。

### 方案3：确保表结构在应用启动前就存在

确保在应用启动前，所有分库的所有分片表都有 `is_delete` 字段。可以：
1. 先执行 SQL 脚本添加字段
2. 然后启动应用

## 验证步骤

1. 确认所有表都有 `is_delete` 字段：
```sql
-- 检查每个库的每个表
USE jiaoyi_0;
SHOW COLUMNS FROM product_sku_0 LIKE 'is_delete';
SHOW COLUMNS FROM product_sku_1 LIKE 'is_delete';
SHOW COLUMNS FROM product_sku_2 LIKE 'is_delete';

USE jiaoyi_1;
SHOW COLUMNS FROM product_sku_0 LIKE 'is_delete';
SHOW COLUMNS FROM product_sku_1 LIKE 'is_delete';
SHOW COLUMNS FROM product_sku_2 LIKE 'is_delete';

USE jiaoyi_2;
SHOW COLUMNS FROM product_sku_0 LIKE 'is_delete';
SHOW COLUMNS FROM product_sku_1 LIKE 'is_delete';
SHOW COLUMNS FROM product_sku_2 LIKE 'is_delete';
```

2. 重启 product-service 应用

3. 测试接口：`GET /api/store-products/merchant/merchant_012`

## 注意事项

- 生产环境建议启用元数据校验（`check-table-metadata-enabled=true`）以确保数据一致性
- 表结构变更后必须重启应用才能生效
- 或者使用 ShardingSphere 的管理接口刷新元数据（如果可用）






