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
 * StockMode 枚举类型处理器
 */
@MappedTypes(Inventory.StockMode.class)
@MappedJdbcTypes(JdbcType.VARCHAR)
public class StockModeTypeHandler extends BaseTypeHandler<Inventory.StockMode> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Inventory.StockMode parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, parameter.name());
    }

    @Override
    public Inventory.StockMode getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String value = rs.getString(columnName);
        if (value == null) {
            return Inventory.StockMode.UNLIMITED; // 默认值
        }
        try {
            return Inventory.StockMode.valueOf(value);
        } catch (IllegalArgumentException e) {
            // 兼容旧数据，如果值不是枚举值，返回默认值
            return Inventory.StockMode.UNLIMITED;
        }
    }

    @Override
    public Inventory.StockMode getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String value = rs.getString(columnIndex);
        if (value == null) {
            return Inventory.StockMode.UNLIMITED; // 默认值
        }
        try {
            return Inventory.StockMode.valueOf(value);
        } catch (IllegalArgumentException e) {
            // 兼容旧数据，如果值不是枚举值，返回默认值
            return Inventory.StockMode.UNLIMITED;
        }
    }

    @Override
    public Inventory.StockMode getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String value = cs.getString(columnIndex);
        if (value == null) {
            return Inventory.StockMode.UNLIMITED; // 默认值
        }
        try {
            return Inventory.StockMode.valueOf(value);
        } catch (IllegalArgumentException e) {
            // 兼容旧数据，如果值不是枚举值，返回默认值
            return Inventory.StockMode.UNLIMITED;
        }
    }
}





