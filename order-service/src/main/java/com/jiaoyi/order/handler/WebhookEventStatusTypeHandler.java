package com.jiaoyi.order.handler;

import com.jiaoyi.order.entity.WebhookEventLog;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * WebhookEventStatus 类型处理器
 */
@MappedTypes(WebhookEventLog.EventStatus.class)
public class WebhookEventStatusTypeHandler extends BaseTypeHandler<WebhookEventLog.EventStatus> {
    
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, WebhookEventLog.EventStatus parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, parameter.name());
    }
    
    @Override
    public WebhookEventLog.EventStatus getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String status = rs.getString(columnName);
        return status == null ? null : WebhookEventLog.EventStatus.valueOf(status);
    }
    
    @Override
    public WebhookEventLog.EventStatus getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String status = rs.getString(columnIndex);
        return status == null ? null : WebhookEventLog.EventStatus.valueOf(status);
    }
    
    @Override
    public WebhookEventLog.EventStatus getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String status = cs.getString(columnIndex);
        return status == null ? null : WebhookEventLog.EventStatus.valueOf(status);
    }
}





