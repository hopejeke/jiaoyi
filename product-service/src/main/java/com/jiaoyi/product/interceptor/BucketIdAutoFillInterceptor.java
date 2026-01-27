package com.jiaoyi.product.interceptor;

import com.jiaoyi.product.util.ProductShardUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.*;

import java.lang.reflect.Field;
import java.util.Properties;

/**
 * MyBatis 拦截器：自动填充 bucket_id
 * 
 * 在 INSERT 时根据 product_shard_id 或 store_id 自动计算并填充 bucket_id
 * 
 * 支持的字段：
 * - product_shard_id -> bucket_id（直接使用）
 * - store_id -> bucket_id（计算 product_shard_id，然后赋值）
 */
@Slf4j
@Intercepts({
    @Signature(type = Executor.class, method = "update", 
               args = {MappedStatement.class, Object.class})
})
public class BucketIdAutoFillInterceptor implements Interceptor {

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        MappedStatement ms = (MappedStatement) invocation.getArgs()[0];
        Object parameter = invocation.getArgs()[1];

        // 只处理 INSERT 操作
        if (ms.getSqlCommandType() == SqlCommandType.INSERT && parameter != null) {
            fillBucketId(parameter);
        }

        return invocation.proceed();
    }

    /**
     * 自动填充 bucket_id
     */
    private void fillBucketId(Object entity) {
        try {
            // 获取 bucket_id 字段
            Field bucketIdField = getField(entity.getClass(), "bucketId");
            if (bucketIdField == null) {
                // 如果没有 bucket_id 字段，跳过
                return;
            }
            bucketIdField.setAccessible(true);
            
            // 检查是否已有值
            Integer currentBucketId = (Integer) bucketIdField.get(entity);
            if (currentBucketId != null && currentBucketId != 0) {
                // 已有值，不覆盖
                return;
            }
            
            // 尝试从 product_shard_id 获取
            Field productShardIdField = getField(entity.getClass(), "productShardId");
            if (productShardIdField != null) {
                productShardIdField.setAccessible(true);
                Integer productShardId = (Integer) productShardIdField.get(entity);
                if (productShardId != null && productShardId != 0) {
                    bucketIdField.set(entity, productShardId);
                    log.debug("Auto-filled bucket_id={} from product_shard_id", productShardId);
                    return;
                }
            }
            
            // 尝试从 store_id 计算
            Field storeIdField = getField(entity.getClass(), "storeId");
            if (storeIdField != null) {
                storeIdField.setAccessible(true);
                Long storeId = (Long) storeIdField.get(entity);
                if (storeId != null) {
                    int bucketId = ProductShardUtil.calculateProductShardId(storeId);
                    bucketIdField.set(entity, bucketId);
                    log.debug("Auto-filled bucket_id={} from store_id={}", bucketId, storeId);
                    return;
                }
            }
            
            // 如果都没有，记录警告（但不抛出异常，因为某些表可能不需要 bucket_id）
            log.debug("无法自动填充 bucket_id，entity={}", entity.getClass().getSimpleName());
        } catch (Exception e) {
            log.warn("自动填充 bucket_id 失败", e);
            // 不抛出异常，避免影响主流程
        }
    }

    /**
     * 递归查找字段（包括父类）
     */
    private Field getField(Class<?> clazz, String fieldName) {
        while (clazz != null) {
            try {
                return clazz.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        // 可以读取配置属性
    }
}


