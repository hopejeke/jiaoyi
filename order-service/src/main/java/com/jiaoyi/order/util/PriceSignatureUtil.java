package com.jiaoyi.order.util;

import com.jiaoyi.order.entity.Order;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.DigestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

/**
 * 价格签名工具类
 * 防止价格篡改
 */
@Slf4j
public class PriceSignatureUtil {

    /**
     * 签名密钥（建议从配置文件读取）
     */
    private static final String SECRET_SALT = "jiaoyi_price_signature_secret_2026";

    /**
     * 生成价格签名
     * @param subtotal 小计
     * @param discount 优惠金额
     * @param deliveryFee 配送费
     * @param taxTotal 税费
     * @param tips 小费
     * @param total 总价
     * @return MD5签名
     */
    public static String generateSignature(BigDecimal subtotal, BigDecimal discount,
                                          BigDecimal deliveryFee, BigDecimal taxTotal,
                                          BigDecimal tips, BigDecimal total) {
        // 统一精度
        subtotal = PriceUtil.roundPrice(subtotal);
        discount = PriceUtil.roundPrice(discount);
        deliveryFee = PriceUtil.roundPrice(deliveryFee);
        taxTotal = PriceUtil.roundPrice(taxTotal);
        tips = PriceUtil.roundPrice(tips);
        total = PriceUtil.roundPrice(total);

        // 构建签名字符串
        String signStr = subtotal + "|" + discount + "|" + deliveryFee + "|" +
                        taxTotal + "|" + tips + "|" + total + "|" + SECRET_SALT;

        // MD5哈希
        String signature = DigestUtils.md5DigestAsHex(signStr.getBytes(StandardCharsets.UTF_8));

        log.debug("生成价格签名，原始字符串: {}, 签名: {}", signStr.replace(SECRET_SALT, "***"), signature);

        return signature;
    }

    /**
     * 从订单价格JSON生成签名
     * @param orderPriceJson 订单价格JSON字符串
     * @return MD5签名
     */
    public static String generateSignature(String orderPriceJson) {
        if (orderPriceJson == null || orderPriceJson.isEmpty()) {
            return "";
        }

        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> priceMap = mapper.readValue(orderPriceJson, java.util.Map.class);

            BigDecimal subtotal = PriceUtil.parse(priceMap.get("subtotal"));
            BigDecimal discount = PriceUtil.parse(priceMap.get("discount"));
            BigDecimal deliveryFee = PriceUtil.parse(priceMap.get("deliveryFee"));
            BigDecimal taxTotal = PriceUtil.parse(priceMap.get("taxTotal"));
            BigDecimal tips = PriceUtil.parse(priceMap.get("tips"));
            BigDecimal total = PriceUtil.parse(priceMap.get("total"));

            return generateSignature(subtotal, discount, deliveryFee, taxTotal, tips, total);

        } catch (Exception e) {
            log.error("解析订单价格JSON失败，无法生成签名", e);
            return "";
        }
    }

    /**
     * 从Order对象生成签名
     * @param order 订单
     * @return MD5签名
     */
    public static String generateSignature(Order order) {
        if (order == null || order.getOrderPrice() == null) {
            return "";
        }
        return generateSignature(order.getOrderPrice());
    }

    /**
     * 验证价格签名
     * @param orderPriceJson 订单价格JSON字符串
     * @param signature 签名
     * @return true表示验证通过
     */
    public static boolean verifySignature(String orderPriceJson, String signature) {
        if (signature == null || signature.isEmpty()) {
            log.warn("签名为空，验证失败");
            return false;
        }

        String expectedSignature = generateSignature(orderPriceJson);
        boolean verified = signature.equals(expectedSignature);

        if (!verified) {
            log.error("价格签名验证失败，期望: {}, 实际: {}", expectedSignature, signature);
        }

        return verified;
    }

    /**
     * 验证Order对象的价格签名
     * @param order 订单
     * @param signature 签名
     * @return true表示验证通过
     */
    public static boolean verifySignature(Order order, String signature) {
        if (order == null || order.getOrderPrice() == null) {
            return false;
        }
        return verifySignature(order.getOrderPrice(), signature);
    }

    /**
     * 验证价格一致性（重新计算签名并比较）
     * @param subtotal 小计
     * @param discount 优惠金额
     * @param deliveryFee 配送费
     * @param taxTotal 税费
     * @param tips 小费
     * @param total 总价
     * @param signature 签名
     * @return true表示验证通过
     */
    public static boolean verifyPriceConsistency(BigDecimal subtotal, BigDecimal discount,
                                                 BigDecimal deliveryFee, BigDecimal taxTotal,
                                                 BigDecimal tips, BigDecimal total,
                                                 String signature) {
        if (signature == null || signature.isEmpty()) {
            log.warn("签名为空，验证失败");
            return false;
        }

        String expectedSignature = generateSignature(subtotal, discount, deliveryFee,
                                                     taxTotal, tips, total);
        boolean verified = signature.equals(expectedSignature);

        if (!verified) {
            log.error("价格一致性验证失败，期望签名: {}, 实际签名: {}", expectedSignature, signature);
            log.error("价格详情 - 小计: {}, 优惠: {}, 配送费: {}, 税费: {}, 小费: {}, 总价: {}",
                    subtotal, discount, deliveryFee, taxTotal, tips, total);
        }

        return verified;
    }
}
