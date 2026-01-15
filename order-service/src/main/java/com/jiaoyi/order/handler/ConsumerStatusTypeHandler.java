package com.jiaoyi.order.handler;

import com.jiaoyi.order.entity.ConsumerLog;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * ConsumerStatus 类型处理器
 */
@MappedTypes(ConsumerLog.ConsumerStatus.class)
public class ConsumerStatusTypeHandler extends BaseTypeHandler<ConsumerLog.ConsumerStatus> {
    
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, ConsumerLog.ConsumerStatus parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, parameter.name());
    }
    
    @Override
    public ConsumerLog.ConsumerStatus getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String status = rs.getString(columnName);
        return status == null ? null : ConsumerLog.ConsumerStatus.valueOf(status);
    }
    
    @Override
    public ConsumerLog.ConsumerStatus getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String status = rs.getString(columnIndex);
        return status == null ? null : ConsumerLog.ConsumerStatus.valueOf(status);
    }
    
    @Override
    public ConsumerLog.ConsumerStatus getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String status = cs.getString(columnIndex);
        return status == null ? null : ConsumerLog.ConsumerStatus.valueOf(status);
    }
}



