package com.jiaoyi.order.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiaoyi.order.entity.Order;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 订单价格工具类
 * 统一处理订单价格JSON的解析
 */
@Slf4j
public class OrderPriceUtil {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private OrderPriceUtil() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * 从订单价格JSON中解析总金额
     * @param order 订单对象
     * @return 总金额，解析失败返回ZERO
     */
    public static BigDecimal parseOrderTotal(Order order) {
        if (order == null || order.getOrderPrice() == null || order.getOrderPrice().isEmpty()) {
            return BigDecimal.ZERO;
        }
        return parseOrderTotal(order.getOrderPrice());
    }

    /**
     * 从订单价格JSON字符串中解析总金额
     * @param orderPriceJson 订单价格JSON字符串
     * @return 总金额，解析失败返回ZERO
     */
    public static BigDecimal parseOrderTotal(String orderPriceJson) {
        if (orderPriceJson == null || orderPriceJson.isEmpty()) {
            return BigDecimal.ZERO;
        }

        try {
            if (orderPriceJson.startsWith("{")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> orderPrice = OBJECT_MAPPER.readValue(orderPriceJson, Map.class);
                Object totalObj = orderPrice.get("total");
                if (totalObj != null) {
                    return PriceUtil.parse(totalObj);
                }
            }
        } catch (Exception e) {
            log.error("解析订单价格JSON失败，orderPriceJson: {}", orderPriceJson, e);
        }

        return BigDecimal.ZERO;
    }

    /**
     * 从订单价格JSON中解析小计
     * @param orderPriceJson 订单价格JSON字符串
     * @return 小计，解析失败返回ZERO
     */
    public static BigDecimal parseOrderSubtotal(String orderPriceJson) {
        return parseField(orderPriceJson, "subtotal");
    }

    /**
     * 从订单价格JSON中解析优惠金额
     * @param orderPriceJson 订单价格JSON字符串
     * @return 优惠金额，解析失败返回ZERO
     */
    public static BigDecimal parseOrderDiscount(String orderPriceJson) {
        return parseField(orderPriceJson, "discount");
    }

    /**
     * 从订单价格JSON中解析配送费
     * @param orderPriceJson 订单价格JSON字符串
     * @return 配送费，解析失败返回ZERO
     */
    public static BigDecimal parseOrderDeliveryFee(String orderPriceJson) {
        return parseField(orderPriceJson, "deliveryFee");
    }

    /**
     * 从订单价格JSON中解析税费
     * @param orderPriceJson 订单价格JSON字符串
     * @return 税费，解析失败返回ZERO
     */
    public static BigDecimal parseOrderTax(String orderPriceJson) {
        return parseField(orderPriceJson, "taxTotal");
    }

    /**
     * 从订单价格JSON中解析小费
     * @param orderPriceJson 订单价格JSON字符串
     * @return 小费，解析失败返回ZERO
     */
    public static BigDecimal parseOrderTips(String orderPriceJson) {
        return parseField(orderPriceJson, "tips");
    }

    /**
     * 从订单价格JSON中解析服务费
     * @param orderPriceJson 订单价格JSON字符串
     * @return 服务费，解析失败返回ZERO
     */
    public static BigDecimal parseOrderCharge(String orderPriceJson) {
        return parseField(orderPriceJson, "charge");
    }

    /**
     * 通用字段解析方法
     * @param orderPriceJson 订单价格JSON字符串
     * @param fieldName 字段名
     * @return 字段值，解析失败返回ZERO
     */
    private static BigDecimal parseField(String orderPriceJson, String fieldName) {
        if (orderPriceJson == null || orderPriceJson.isEmpty()) {
            return BigDecimal.ZERO;
        }

        try {
            if (orderPriceJson.startsWith("{")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> orderPrice = OBJECT_MAPPER.readValue(orderPriceJson, Map.class);
                Object fieldValue = orderPrice.get(fieldName);
                if (fieldValue != null) {
                    return PriceUtil.parse(fieldValue);
                }
            }
        } catch (Exception e) {
            log.error("解析订单价格字段失败，fieldName: {}, orderPriceJson: {}", fieldName, orderPriceJson, e);
        }

        return BigDecimal.ZERO;
    }

    /**
     * 验证订单价格是否有效
     * @param order 订单对象
     * @return true表示有效
     */
    public static boolean isValidOrderPrice(Order order) {
        if (order == null || order.getOrderPrice() == null || order.getOrderPrice().isEmpty()) {
            return false;
        }

        BigDecimal total = parseOrderTotal(order);
        return PriceUtil.isValid(total);
    }
}
