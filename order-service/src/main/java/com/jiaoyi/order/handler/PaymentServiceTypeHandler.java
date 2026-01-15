package com.jiaoyi.order.handler;

import com.jiaoyi.order.enums.PaymentServiceEnum;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * PaymentServiceEnum 的 TypeHandler
 * 用于 MyBatis 映射 payment_service 字段到 PaymentServiceEnum
 */
@MappedTypes(PaymentServiceEnum.class)
@MappedJdbcTypes(JdbcType.VARCHAR)
public class PaymentServiceTypeHandler extends BaseTypeHandler<PaymentServiceEnum> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, PaymentServiceEnum parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, parameter.getCode());
    }

    @Override
    public PaymentServiceEnum getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String value = rs.getString(columnName);
        return value == null ? null : PaymentServiceEnum.fromCode(value);
    }

    @Override
    public PaymentServiceEnum getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String value = rs.getString(columnIndex);
        return value == null ? null : PaymentServiceEnum.fromCode(value);
    }

    @Override
    public PaymentServiceEnum getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String value = cs.getString(columnIndex);
        return value == null ? null : PaymentServiceEnum.fromCode(value);
    }
}






