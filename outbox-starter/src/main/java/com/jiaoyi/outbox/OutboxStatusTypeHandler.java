package com.jiaoyi.outbox;

import com.jiaoyi.outbox.entity.Outbox;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * OutboxStatus 枚举类型处理器（通用组件）
 * 用于 MyBatis 将数据库的 VARCHAR 类型映射为 Outbox.OutboxStatus 枚举
 */
@MappedTypes(Outbox.OutboxStatus.class)
@MappedJdbcTypes(JdbcType.VARCHAR)
public class OutboxStatusTypeHandler extends BaseTypeHandler<Outbox.OutboxStatus> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Outbox.OutboxStatus parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, parameter.name());
    }

    @Override
    public Outbox.OutboxStatus getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String value = rs.getString(columnName);
        return value == null ? null : Outbox.OutboxStatus.valueOf(value);
    }

    @Override
    public Outbox.OutboxStatus getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String value = rs.getString(columnIndex);
        return value == null ? null : Outbox.OutboxStatus.valueOf(value);
    }

    @Override
    public Outbox.OutboxStatus getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String value = cs.getString(columnIndex);
        return value == null ? null : Outbox.OutboxStatus.valueOf(value);
    }
}





