package com.jiaoyi.product.handler;

import com.jiaoyi.product.entity.ProductSku;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * ProductSku.SkuStatus 枚举类型处理器
 */
@MappedTypes(ProductSku.SkuStatus.class)
@MappedJdbcTypes(JdbcType.VARCHAR)
public class SkuStatusTypeHandler extends BaseTypeHandler<ProductSku.SkuStatus> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, ProductSku.SkuStatus parameter, JdbcType jdbcType) throws SQLException {
        ps.setString(i, parameter.name());
    }

    @Override
    public ProductSku.SkuStatus getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String value = rs.getString(columnName);
        return value == null ? null : ProductSku.SkuStatus.valueOf(value);
    }

    @Override
    public ProductSku.SkuStatus getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String value = rs.getString(columnIndex);
        return value == null ? null : ProductSku.SkuStatus.valueOf(value);
    }

    @Override
    public ProductSku.SkuStatus getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String value = cs.getString(columnIndex);
        return value == null ? null : ProductSku.SkuStatus.valueOf(value);
    }
}









