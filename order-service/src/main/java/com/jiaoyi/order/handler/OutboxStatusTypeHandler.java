package com.jiaoyi.order.handler;

import com.jiaoyi.order.entity.Outbox;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Outbox状态类型处理器
 */
@MappedTypes(Outbox.OutboxStatus.class)
public class OutboxStatusTypeHandler extends BaseTypeHandler<Outbox.OutboxStatus> {
    
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Outbox.OutboxStatus parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, parameter.name());
    }
    
    @Override
    public Outbox.OutboxStatus getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String status = rs.getString(columnName);
        return status == null ? null : Outbox.OutboxStatus.valueOf(status);
    }
    
    @Override
    public Outbox.OutboxStatus getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String status = rs.getString(columnIndex);
        return status == null ? null : Outbox.OutboxStatus.valueOf(status);
    }
    
    @Override
    public Outbox.OutboxStatus getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String status = cs.getString(columnIndex);
        return status == null ? null : Outbox.OutboxStatus.valueOf(status);
    }
}

