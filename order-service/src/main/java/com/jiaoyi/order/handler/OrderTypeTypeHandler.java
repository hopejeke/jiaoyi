package com.jiaoyi.order.handler;

import com.jiaoyi.order.enums.OrderTypeEnum;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * OrderTypeEnum 的 TypeHandler
 * 用于 MyBatis 映射 order_type 字段到 OrderTypeEnum
 */
@MappedTypes(OrderTypeEnum.class)
@MappedJdbcTypes(JdbcType.VARCHAR)
public class OrderTypeTypeHandler extends BaseTypeHandler<OrderTypeEnum> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, OrderTypeEnum parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, parameter.getCode());
    }

    @Override
    public OrderTypeEnum getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String value = rs.getString(columnName);
        return value == null ? null : OrderTypeEnum.fromCode(value);
    }

    @Override
    public OrderTypeEnum getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String value = rs.getString(columnIndex);
        return value == null ? null : OrderTypeEnum.fromCode(value);
    }

    @Override
    public OrderTypeEnum getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String value = cs.getString(columnIndex);
        return value == null ? null : OrderTypeEnum.fromCode(value);
    }
}








