package com.jiaoyi.product.typehandler;

import com.jiaoyi.product.entity.StoreProduct.StoreProductStatus;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * StoreProductStatus 枚举类型处理器
 * 
 * 处理数据库中可能存在的数字值（0, 1）和字符串值（ACTIVE, INACTIVE）
 * 
 * 映射规则：
 * - 0 或 "0" -> ACTIVE
 * - 1 或 "1" -> INACTIVE
 * - "ACTIVE" -> ACTIVE
 * - "INACTIVE" -> INACTIVE
 */
@MappedTypes(StoreProductStatus.class)
@MappedJdbcTypes(JdbcType.VARCHAR)
public class StoreProductStatusTypeHandler extends BaseTypeHandler<StoreProductStatus> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, StoreProductStatus parameter, JdbcType jdbcType) throws SQLException {
        // 写入数据库时，使用枚举名称
        ps.setString(i, parameter.name());
    }

    @Override
    public StoreProductStatus getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String value = rs.getString(columnName);
        return convertToEnum(value);
    }

    @Override
    public StoreProductStatus getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String value = rs.getString(columnIndex);
        return convertToEnum(value);
    }

    @Override
    public StoreProductStatus getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String value = cs.getString(columnIndex);
        return convertToEnum(value);
    }

    /**
     * 将数据库值转换为枚举
     * 
     * @param value 数据库值（可能是 "0", "1", "ACTIVE", "INACTIVE"）
     * @return 枚举值
     */
    private StoreProductStatus convertToEnum(String value) {
        if (value == null || value.isEmpty()) {
            // 默认返回 ACTIVE
            return StoreProductStatus.ACTIVE;
        }
        
        // 处理数字值
        if ("0".equals(value)) {
            return StoreProductStatus.ACTIVE;
        }
        if ("1".equals(value)) {
            return StoreProductStatus.INACTIVE;
        }
        
        // 处理字符串值
        try {
            return StoreProductStatus.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            // 如果无法解析，默认返回 ACTIVE
            return StoreProductStatus.ACTIVE;
        }
    }
}


