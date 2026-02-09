package com.jiaoyi.common.constants;

import lombok.Getter;

/**
 * 统一响应码枚举
 * 规范：
 * - 200: 成功
 * - 400-499: 客户端错误
 * - 500-599: 服务端错误
 * - 1000-1999: 订单相关业务错误
 * - 2000-2999: 支付相关业务错误
 * - 3000-3999: 商品/库存相关业务错误
 * - 4000-4999: 优惠券相关业务错误
 */
@Getter
public enum ResponseCode {

    // ========== 通用响应码 ==========
    SUCCESS(200, "操作成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未授权"),
    FORBIDDEN(403, "禁止访问"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "系统内部错误"),
    SERVICE_UNAVAILABLE(503, "服务暂时不可用"),

    // ========== 订单相关 (1000-1999) ==========
    ORDER_NOT_FOUND(1001, "订单不存在"),
    ORDER_STATUS_ERROR(1002, "订单状态不正确"),
    ORDER_ALREADY_PAID(1003, "订单已支付，请勿重复支付"),
    ORDER_ALREADY_CANCELLED(1004, "订单已取消"),
    ORDER_ALREADY_COMPLETED(1005, "订单已完成"),
    ORDER_TIMEOUT(1006, "订单已超时"),
    ORDER_DUPLICATE_SUBMIT(1007, "请勿重复提交相同订单"),
    ORDER_USER_BUSY(1008, "您有订单正在处理中，请稍后再试"),
    ORDER_MERCHANT_BUSY(1009, "商家当前繁忙，请稍后再试"),
    ORDER_ITEMS_EMPTY(1010, "订单项不能为空"),
    ORDER_ITEM_INVALID(1011, "订单项数据不完整"),
    ORDER_AMOUNT_ERROR(1012, "订单金额异常"),
    ORDER_PRICE_TAMPERED(1013, "价格签名验证失败，请重新下单"),

    // ========== 支付相关 (2000-2999) ==========
    PAYMENT_NOT_FOUND(2001, "支付记录不存在"),
    PAYMENT_AMOUNT_MISMATCH(2002, "支付金额不匹配"),
    PAYMENT_FAILED(2003, "支付失败"),
    PAYMENT_PROCESSING(2004, "支付正在处理中，请勿重复提交"),
    PAYMENT_METHOD_INVALID(2005, "支付方式无效"),
    PAYMENT_ALREADY_SUCCESS(2006, "该订单已支付成功"),
    PAYMENT_CALLBACK_VERIFY_FAILED(2007, "支付回调验证失败"),

    // ========== 库存相关 (3000-3999) ==========
    PRODUCT_NOT_FOUND(3001, "商品不存在"),
    PRODUCT_PRICE_INVALID(3002, "商品价格无效"),
    STOCK_INSUFFICIENT(3003, "库存不足或锁定失败"),
    STOCK_LOCK_FAILED(3004, "库存锁定失败"),
    STOCK_UNLOCK_FAILED(3005, "库存解锁失败"),

    // ========== 优惠券相关 (4000-4999) ==========
    COUPON_NOT_FOUND(4001, "优惠券不存在或不可用"),
    COUPON_EXPIRED(4002, "优惠券已过期"),
    COUPON_VALIDATE_FAILED(4003, "优惠券验证失败"),
    COUPON_CALCULATE_FAILED(4004, "优惠金额计算失败"),

    // ========== 配送相关 (5000-5999) ==========
    DELIVERY_NOT_AVAILABLE(5001, "当前配送服务不可用"),
    DELIVERY_QUOTE_FAILED(5002, "配送报价失败"),
    DELIVERY_CREATE_FAILED(5003, "创建配送订单失败"),

    // ========== 系统相关 (9000-9999) ==========
    SYSTEM_BUSY(9001, "系统繁忙，请稍后重试"),
    LOCK_ACQUIRE_FAILED(9002, "获取锁失败，请稍后重试"),
    REMOTE_SERVICE_ERROR(9003, "远程服务调用失败"),
    DATA_PARSE_ERROR(9004, "数据解析失败");

    private final int code;
    private final String message;

    ResponseCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    /**
     * 根据code获取枚举
     */
    public static ResponseCode getByCode(int code) {
        for (ResponseCode responseCode : values()) {
            if (responseCode.code == code) {
                return responseCode;
            }
        }
        return null;
    }
}
