package com.jiaoyi.product.handler;

import com.jiaoyi.product.entity.Inventory;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * RestoreMode 枚举类型处理器
 */
@MappedTypes(Inventory.RestoreMode.class)
@MappedJdbcTypes(JdbcType.VARCHAR)
public class RestoreModeTypeHandler extends BaseTypeHandler<Inventory.RestoreMode> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Inventory.RestoreMode parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, parameter.name());
    }

    @Override
    public Inventory.RestoreMode getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String value = rs.getString(columnName);
        if (value == null) {
            return Inventory.RestoreMode.MANUAL; // 默认值
        }
        try {
            return Inventory.RestoreMode.valueOf(value);
        } catch (IllegalArgumentException e) {
            // 兼容旧数据，如果值不是枚举值，返回默认值
            return Inventory.RestoreMode.MANUAL;
        }
    }

    @Override
    public Inventory.RestoreMode getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String value = rs.getString(columnIndex);
        if (value == null) {
            return Inventory.RestoreMode.MANUAL; // 默认值
        }
        try {
            return Inventory.RestoreMode.valueOf(value);
        } catch (IllegalArgumentException e) {
            // 兼容旧数据，如果值不是枚举值，返回默认值
            return Inventory.RestoreMode.MANUAL;
        }
    }

    @Override
    public Inventory.RestoreMode getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String value = cs.getString(columnIndex);
        if (value == null) {
            return Inventory.RestoreMode.MANUAL; // 默认值
        }
        try {
            return Inventory.RestoreMode.valueOf(value);
        } catch (IllegalArgumentException e) {
            // 兼容旧数据，如果值不是枚举值，返回默认值
            return Inventory.RestoreMode.MANUAL;
        }
    }
}
