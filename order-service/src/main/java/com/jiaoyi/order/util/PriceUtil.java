package com.jiaoyi.order.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 价格计算工具类
 * 统一处理BigDecimal精度，防止精度损失
 */
public class PriceUtil {

    /**
     * 价格小数位数（统一2位）
     */
    public static final int DECIMAL_SCALE = 2;

    /**
     * 舍入模式（四舍五入）
     */
    public static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    /**
     * 四舍五入到2位小数
     * @param price 原始价格
     * @return 四舍五入后的价格
     */
    public static BigDecimal roundPrice(BigDecimal price) {
        if (price == null) {
            return BigDecimal.ZERO;
        }
        return price.setScale(DECIMAL_SCALE, ROUNDING_MODE);
    }

    /**
     * 价格相乘（数量 * 单价）
     * @param unitPrice 单价
     * @param quantity 数量
     * @return 总价（四舍五入到2位小数）
     */
    public static BigDecimal multiply(BigDecimal unitPrice, int quantity) {
        if (unitPrice == null) {
            return BigDecimal.ZERO;
        }
        return unitPrice.multiply(BigDecimal.valueOf(quantity))
                .setScale(DECIMAL_SCALE, ROUNDING_MODE);
    }

    /**
     * 价格相加
     * @param prices 价格列表
     * @return 总价（四舍五入到2位小数）
     */
    public static BigDecimal add(BigDecimal... prices) {
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal price : prices) {
            if (price != null) {
                sum = sum.add(price);
            }
        }
        return sum.setScale(DECIMAL_SCALE, ROUNDING_MODE);
    }

    /**
     * 价格相减
     * @param price1 被减数
     * @param price2 减数
     * @return 差价（四舍五入到2位小数）
     */
    public static BigDecimal subtract(BigDecimal price1, BigDecimal price2) {
        if (price1 == null) {
            price1 = BigDecimal.ZERO;
        }
        if (price2 == null) {
            price2 = BigDecimal.ZERO;
        }
        return price1.subtract(price2).setScale(DECIMAL_SCALE, ROUNDING_MODE);
    }

    /**
     * 计算百分比折扣
     * @param originalPrice 原价
     * @param discountPercent 折扣百分比（例如：10表示打9折，即优惠10%）
     * @return 折扣金额（四舍五入到2位小数）
     */
    public static BigDecimal calculateDiscount(BigDecimal originalPrice, BigDecimal discountPercent) {
        if (originalPrice == null || discountPercent == null) {
            return BigDecimal.ZERO;
        }
        return originalPrice.multiply(discountPercent)
                .divide(BigDecimal.valueOf(100), DECIMAL_SCALE, ROUNDING_MODE);
    }

    /**
     * 解析字符串为BigDecimal
     * @param priceStr 价格字符串
     * @return 价格（四舍五入到2位小数）
     */
    public static BigDecimal parse(String priceStr) {
        if (priceStr == null || priceStr.isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(priceStr).setScale(DECIMAL_SCALE, ROUNDING_MODE);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 解析Object为BigDecimal
     * @param priceObj 价格对象
     * @return 价格（四舍五入到2位小数）
     */
    public static BigDecimal parse(Object priceObj) {
        if (priceObj == null) {
            return BigDecimal.ZERO;
        }
        return parse(priceObj.toString());
    }

    /**
     * 比较两个价格是否相等（忽略精度差异）
     * @param price1 价格1
     * @param price2 价格2
     * @return true表示相等
     */
    public static boolean equals(BigDecimal price1, BigDecimal price2) {
        if (price1 == null && price2 == null) {
            return true;
        }
        if (price1 == null || price2 == null) {
            return false;
        }
        return roundPrice(price1).compareTo(roundPrice(price2)) == 0;
    }

    /**
     * 验证价格是否有效（大于0）
     * @param price 价格
     * @return true表示有效
     */
    public static boolean isValid(BigDecimal price) {
        return price != null && price.compareTo(BigDecimal.ZERO) > 0;
    }
}
