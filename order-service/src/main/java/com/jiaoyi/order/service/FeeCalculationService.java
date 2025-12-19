package com.jiaoyi.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiaoyi.common.exception.BusinessException;
import com.jiaoyi.order.entity.MerchantFeeConfig;
import com.jiaoyi.order.entity.Order;
import com.jiaoyi.order.mapper.MerchantFeeConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * 费用计算服务
 * 负责计算订单的配送费、税费、在线服务费等
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FeeCalculationService {

    private final MerchantFeeConfigMapper merchantFeeConfigMapper;
    private final ObjectMapper objectMapper;
    private final GoogleMapsService googleMapsService;

    /**
     * 计算配送费（支持 DoorDash 报价）
     * 如果是 DoorDash 配送，优先调用 DoorDash 报价 API
     * 否则使用本地配置计算
     * 
     * @param order 订单信息
     * @param subtotal 订单小计
     * @param useDoorDashQuote 是否使用 DoorDash 报价（如果商户配置了 DoorDash）
     * @return 配送费
     */
    public BigDecimal calculateDeliveryFee(Order order, BigDecimal subtotal, boolean useDoorDashQuote) {
        // 如果是 DoorDash 配送且需要报价，调用 DoorDash API
        if (useDoorDashQuote && "DELIVERY".equalsIgnoreCase(order.getOrderType())) {
            try {
                // 这里需要注入 DoorDashService，暂时先返回本地计算
                // 实际实现时会在 OrderService 中调用 DoorDashService.quoteDelivery()
                log.debug("DoorDash 配送，但报价功能需要在 OrderService 中调用");
            } catch (Exception e) {
                log.warn("DoorDash 报价失败，使用本地计算，订单ID: {}", order.getId(), e);
            }
        }
        
        return calculateDeliveryFeeInternal(order, subtotal);
    }
    
    /**
     * 计算配送费（内部方法，使用本地配置）
     * 参照 OO 项目的 onlineDeliveryFeeCalculation 方法
     */
    public BigDecimal calculateDeliveryFee(Order order, BigDecimal subtotal) {
        return calculateDeliveryFeeInternal(order, subtotal);
    }
    
    /**
     * 计算配送费（内部实现）
     */
    private BigDecimal calculateDeliveryFeeInternal(Order order, BigDecimal subtotal) {
        String merchantId = order.getMerchantId();
        String orderType = order.getOrderType();

        // 只有配送订单才需要配送费
        if (!"DELIVERY".equalsIgnoreCase(orderType)) {
            log.debug("订单类型为 {}，不需要配送费，订单ID: {}", orderType, order.getId());
            return BigDecimal.ZERO;
        }

        // 获取商户费用配置
        MerchantFeeConfig config = merchantFeeConfigMapper.selectByMerchantId(merchantId);
        if (config == null) {
            // 使用默认配置：固定配送费 5 元
            log.debug("商户 {} 未配置费用，使用默认配送费 5 元", merchantId);
            return new BigDecimal("5.00");
        }

        String deliveryFeeType = config.getDeliveryFeeType();
        if (deliveryFeeType == null) {
            deliveryFeeType = "FLAT_RATE";
        }

        // 检查是否达到免配送费门槛
        BigDecimal freeThreshold = config.getDeliveryFeeFreeThreshold();
        if (freeThreshold != null && freeThreshold.compareTo(BigDecimal.ZERO) > 0) {
            if (subtotal.compareTo(freeThreshold) >= 0) {
                log.debug("订单金额 {} 达到免配送费门槛 {}，免配送费", subtotal, freeThreshold);
                return BigDecimal.ZERO;
            }
        }

        BigDecimal deliveryFee = BigDecimal.ZERO;

        // 根据类型计算配送费（参照 OO 项目逻辑）
        if ("ZONE_RATE".equalsIgnoreCase(deliveryFeeType)) {
            // 按邮编区域计算
            deliveryFee = calculateZoneRateDeliveryFee(order, config);
        } else if ("VARIABLE_RATE".equalsIgnoreCase(deliveryFeeType)) {
            // 按距离的可变费率计算
            deliveryFee = calculateVariableRateDeliveryFee(order, config);
        } else {
            // FLAT_RATE 固定费率（默认）
            BigDecimal fixed = config.getDeliveryFeeFixed();
            if (fixed != null && fixed.compareTo(BigDecimal.ZERO) > 0) {
                deliveryFee = fixed;
            } else {
                // 默认 5 元
                deliveryFee = new BigDecimal("5.00");
            }
        }

        // 应用最低和最高限制
        BigDecimal min = config.getDeliveryFeeMin();
        BigDecimal max = config.getDeliveryFeeMax();
        if (min != null && min.compareTo(BigDecimal.ZERO) > 0 && deliveryFee.compareTo(min) < 0) {
            deliveryFee = min;
        }
        if (max != null && max.compareTo(BigDecimal.ZERO) > 0 && deliveryFee.compareTo(max) > 0) {
            deliveryFee = max;
        }

        log.debug("计算配送费，商户ID: {}, 订单类型: {}, 配送费类型: {}, 订单金额: {}, 配送费: {}", 
                merchantId, orderType, deliveryFeeType, subtotal, deliveryFee);
        return deliveryFee.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 按邮编区域计算配送费（ZONE_RATE）
     */
    private BigDecimal calculateZoneRateDeliveryFee(Order order, MerchantFeeConfig config) {
        String deliveryZoneRateJson = config.getDeliveryZoneRate();
        if (deliveryZoneRateJson == null || deliveryZoneRateJson.isEmpty()) {
            log.warn("商户 {} 配置了 ZONE_RATE 但未配置 deliveryZoneRate，使用固定配送费", order.getMerchantId());
            return config.getDeliveryFeeFixed() != null && config.getDeliveryFeeFixed().compareTo(BigDecimal.ZERO) > 0
                    ? config.getDeliveryFeeFixed() : new BigDecimal("5.00");
        }

        // 从订单中提取邮编
        String zipCode = extractZipCodeFromOrder(order);
        if (zipCode == null || zipCode.isEmpty()) {
            log.warn("订单 {} 未提供邮编，无法使用 ZONE_RATE 计算配送费", order.getId());
            throw new BusinessException("配送地址缺少邮编信息，无法计算配送费");
        }

        // 解析邮编区域配置
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> zoneRates = objectMapper.readValue(deliveryZoneRateJson, List.class);
            if (zoneRates == null || zoneRates.isEmpty()) {
                log.warn("商户 {} 的 deliveryZoneRate 配置为空", order.getMerchantId());
                return config.getDeliveryFeeFixed() != null && config.getDeliveryFeeFixed().compareTo(BigDecimal.ZERO) > 0
                        ? config.getDeliveryFeeFixed() : new BigDecimal("5.00");
            }

            // 处理邮编格式（可能包含 "-"，只取前5位）
            if (zipCode.contains("-")) {
                zipCode = zipCode.split("-")[0];
            }

            // 查找匹配的邮编区域
            for (Map<String, Object> zoneRate : zoneRates) {
                @SuppressWarnings("unchecked")
                List<String> zipcodes = (List<String>) zoneRate.get("zipcodes");
                Object priceObj = zoneRate.get("price");

                if (zipcodes != null && priceObj != null) {
                    if (zipcodes.contains(zipCode)) {
                        BigDecimal price = new BigDecimal(priceObj.toString());
                        log.debug("找到匹配的邮编区域，邮编: {}, 配送费: {}", zipCode, price);
                        return price;
                    }
                }
            }

            // 如果邮编不在任何区域，抛出错误（参照 OO 项目）
            log.warn("订单 {} 的邮编 {} 不在任何配送区域内", order.getId(), zipCode);
            throw new BusinessException("该邮编不在配送范围内");

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("解析 deliveryZoneRate 失败，商户ID: {}", order.getMerchantId(), e);
            throw new BusinessException("配送费配置解析失败: " + e.getMessage());
        }
    }

    /**
     * 按距离的可变费率计算配送费（VARIABLE_RATE）
     */
    private BigDecimal calculateVariableRateDeliveryFee(Order order, MerchantFeeConfig config) {
        String deliveryVariableRateJson = config.getDeliveryVariableRate();
        if (deliveryVariableRateJson == null || deliveryVariableRateJson.isEmpty()) {
            log.warn("商户 {} 配置了 VARIABLE_RATE 但未配置 deliveryVariableRate，使用固定配送费", order.getMerchantId());
            return config.getDeliveryFeeFixed() != null && config.getDeliveryFeeFixed().compareTo(BigDecimal.ZERO) > 0
                    ? config.getDeliveryFeeFixed() : new BigDecimal("5.00");
        }

        // 获取商户坐标
        BigDecimal merchantLat = config.getMerchantLatitude();
        BigDecimal merchantLon = config.getMerchantLongitude();
        if (merchantLat == null || merchantLon == null) {
            log.warn("商户 {} 未配置坐标，无法使用 VARIABLE_RATE 计算配送费", order.getMerchantId());
            throw new BusinessException("商户未配置坐标信息，无法计算配送距离");
        }

        // 从订单中提取配送地址坐标
        DeliveryLocation deliveryLocation = extractDeliveryLocationFromOrder(order);
        if (deliveryLocation == null || deliveryLocation.getLatitude() == null || deliveryLocation.getLongitude() == null) {
            log.warn("订单 {} 未提供配送地址坐标，无法使用 VARIABLE_RATE 计算配送费", order.getId());
            throw new BusinessException("配送地址缺少坐标信息，无法计算配送距离");
        }

        // 计算配送距离（米）
        double distanceMeter = googleMapsService.calculateDistance(
                merchantLat.doubleValue(), merchantLon.doubleValue(),
                deliveryLocation.getLatitude().doubleValue(), deliveryLocation.getLongitude().doubleValue()
        );

        // 检查最大配送距离
        BigDecimal maxDistance = config.getDeliveryMaximumDistance();
        if (maxDistance != null && maxDistance.compareTo(BigDecimal.ZERO) > 0) {
            double maxDistanceMeter = googleMapsService.mileToMeter(maxDistance.doubleValue());
            if (distanceMeter > maxDistanceMeter) {
                log.warn("订单 {} 的配送距离 {} 米超过最大配送距离 {} 米", order.getId(), distanceMeter, maxDistanceMeter);
                throw new BusinessException("配送距离超过最大配送范围");
            }
        }

        // 将距离转换为英里
        double distanceMile = googleMapsService.meterToMile(distanceMeter);

        // 解析可变费率配置
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> variableRates = objectMapper.readValue(deliveryVariableRateJson, List.class);
            if (variableRates == null || variableRates.isEmpty()) {
                log.warn("商户 {} 的 deliveryVariableRate 配置为空", order.getMerchantId());
                return config.getDeliveryFeeFixed() != null && config.getDeliveryFeeFixed().compareTo(BigDecimal.ZERO) > 0
                        ? config.getDeliveryFeeFixed() : new BigDecimal("5.00");
            }

            // 检查距离是否小于第一个费率段的起始距离
            if (variableRates.size() > 0) {
                Map<String, Object> firstRate = variableRates.getFirst();
                Object fromObj = firstRate.get("from");
                if (fromObj != null) {
                    double firstFromMile = Double.parseDouble(fromObj.toString());
                    double firstFromMeter = googleMapsService.mileToMeter(firstFromMile);
                    if (distanceMeter < firstFromMeter) {
                        log.warn("订单 {} 的配送距离 {} 米小于最小配送距离 {} 米", order.getId(), distanceMeter, firstFromMeter);
                        throw new BusinessException("配送距离过近，不在配送范围内");
                    }
                }
            }

            // 检查距离是否超过最后一个费率段的结束距离
            if (variableRates.size() > 0) {
                Map<String, Object> lastRate = variableRates.get(variableRates.size() - 1);
                Object toObj = lastRate.get("to");
                Object priceObj = lastRate.get("price");
                if (toObj != null && priceObj != null) {
                    double lastToMile = Double.parseDouble(toObj.toString());
                    double lastToMeter = googleMapsService.mileToMeter(lastToMile);
                    if (distanceMeter > lastToMeter) {
                        // 超过最大距离，返回最后一个费率段的配送费
                        BigDecimal price = new BigDecimal(priceObj.toString());
                        log.debug("配送距离 {} 米超过最大费率段 {} 米，使用最后一个费率段的配送费: {}", 
                                distanceMeter, lastToMeter, price);
                        return price;
                    }
                }
            }

            // 查找匹配的距离段
            for (Map<String, Object> variableRate : variableRates) {
                Object fromObj = variableRate.get("from");
                Object toObj = variableRate.get("to");
                Object priceObj = variableRate.get("price");

                if (fromObj != null && toObj != null && priceObj != null) {
                    double fromMile = Double.parseDouble(fromObj.toString());
                    double toMile = Double.parseDouble(toObj.toString());
                    double fromMeter = googleMapsService.mileToMeter(fromMile);
                    double toMeter = googleMapsService.mileToMeter(toMile);

                    if (distanceMeter > fromMeter && distanceMeter <= toMeter) {
                        BigDecimal price = new BigDecimal(priceObj.toString());
                        log.debug("找到匹配的距离段，距离: {} 米 ({} 英里)，配送费: {}", distanceMeter, distanceMile, price);
                        return price;
                    }
                }
            }

            // 如果未找到匹配的距离段，使用固定配送费
            log.warn("订单 {} 的配送距离 {} 米未匹配到任何费率段，使用固定配送费", order.getId(), distanceMeter);
            return config.getDeliveryFeeFixed() != null && config.getDeliveryFeeFixed().compareTo(BigDecimal.ZERO) > 0
                    ? config.getDeliveryFeeFixed() : new BigDecimal("5.00");

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("解析 deliveryVariableRate 失败，商户ID: {}", order.getMerchantId(), e);
            throw new BusinessException("配送费配置解析失败: " + e.getMessage());
        }
    }

    /**
     * 从订单中提取邮编
     */
    private String extractZipCodeFromOrder(Order order) {
        try {
            // 从 customerInfo 或 deliveryAddress 中提取邮编
            String customerInfo = order.getCustomerInfo();
            String deliveryAddress = order.getDeliveryAddress();

            // 尝试从 customerInfo JSON 中提取
            if (customerInfo != null && !customerInfo.isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> customerInfoMap = objectMapper.readValue(customerInfo, Map.class);
                if (customerInfoMap != null) {
                    Object zipCodeObj = customerInfoMap.get("zipCode");
                    if (zipCodeObj != null) {
                        return zipCodeObj.toString();
                    }
                    // 尝试从 address 中提取
                    Object addressObj = customerInfoMap.get("address");
                    if (addressObj != null) {
                        String address = addressObj.toString();
                        // 简单提取邮编（假设邮编是5位数字）
                        String zipCode = extractZipCodeFromAddress(address);
                        if (zipCode != null) {
                            return zipCode;
                        }
                    }
                }
            }

            // 尝试从 deliveryAddress JSON 中提取
            if (deliveryAddress != null && !deliveryAddress.isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> deliveryAddressMap = objectMapper.readValue(deliveryAddress, Map.class);
                if (deliveryAddressMap != null) {
                    Object zipCodeObj = deliveryAddressMap.get("zipCode");
                    if (zipCodeObj != null) {
                        return zipCodeObj.toString();
                    }
                }
            }

        } catch (Exception e) {
            log.warn("从订单中提取邮编失败，订单ID: {}", order.getId(), e);
        }
        return null;
    }

    /**
     * 从地址字符串中提取邮编（简单实现）
     */
    private String extractZipCodeFromAddress(String address) {
        if (address == null || address.isEmpty()) {
            return null;
        }
        // 尝试匹配5位数字的邮编
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\b\\d{5}(-\\d{4})?\\b");
        java.util.regex.Matcher matcher = pattern.matcher(address);
        if (matcher.find()) {
            return matcher.group().split("-")[0]; // 只取前5位
        }
        return null;
    }

    /**
     * 从订单中提取配送地址坐标
     */
    private DeliveryLocation extractDeliveryLocationFromOrder(Order order) {
        try {
            String customerInfo = order.getCustomerInfo();
            String deliveryAddress = order.getDeliveryAddress();

            // 尝试从 customerInfo JSON 中提取
            if (customerInfo != null && !customerInfo.isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> customerInfoMap = objectMapper.readValue(customerInfo, Map.class);
                if (customerInfoMap != null) {
                    Object latitudeObj = customerInfoMap.get("latitude");
                    Object longitudeObj = customerInfoMap.get("longitude");
                    if (latitudeObj != null && longitudeObj != null) {
                        return new DeliveryLocation(
                                new BigDecimal(latitudeObj.toString()),
                                new BigDecimal(longitudeObj.toString())
                        );
                    }
                    // 尝试从 coordinates 对象中提取
                    @SuppressWarnings("unchecked")
                    Map<String, Object> coordinates = (Map<String, Object>) customerInfoMap.get("coordinates");
                    if (coordinates != null) {
                        Object latObj = coordinates.get("latitude");
                        Object lonObj = coordinates.get("longitude");
                        if (latObj != null && lonObj != null) {
                            return new DeliveryLocation(
                                    new BigDecimal(latObj.toString()),
                                    new BigDecimal(lonObj.toString())
                            );
                        }
                    }
                }
            }

            // 尝试从 deliveryAddress JSON 中提取
            if (deliveryAddress != null && !deliveryAddress.isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> deliveryAddressMap = objectMapper.readValue(deliveryAddress, Map.class);
                if (deliveryAddressMap != null) {
                    Object latitudeObj = deliveryAddressMap.get("latitude");
                    Object longitudeObj = deliveryAddressMap.get("longitude");
                    if (latitudeObj != null && longitudeObj != null) {
                        return new DeliveryLocation(
                                new BigDecimal(latitudeObj.toString()),
                                new BigDecimal(longitudeObj.toString())
                        );
                    }
                }
            }

        } catch (Exception e) {
            log.warn("从订单中提取配送地址坐标失败，订单ID: {}", order.getId(), e);
        }
        return null;
    }

    /**
     * 配送地址位置信息
     */
    private static class DeliveryLocation {
        private final BigDecimal latitude;
        private final BigDecimal longitude;

        public DeliveryLocation(BigDecimal latitude, BigDecimal longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }

        public BigDecimal getLatitude() {
            return latitude;
        }

        public BigDecimal getLongitude() {
            return longitude;
        }
    }

    /**
     * 计算税费
     */
    public BigDecimal calculateTax(Order order, BigDecimal subtotal, BigDecimal discountAmount) {
        String merchantId = order.getMerchantId();

        // 获取商户费用配置
        MerchantFeeConfig config = merchantFeeConfigMapper.selectByMerchantId(merchantId);
        if (config == null) {
            log.debug("商户 {} 未配置费用，税费为 0", merchantId);
            return BigDecimal.ZERO;
        }

        // 检查是否免税
        if (config.getTaxExempt() != null && config.getTaxExempt()) {
            log.debug("商户 {} 免税，税费为 0", merchantId);
            return BigDecimal.ZERO;
        }

        // 计算税费（基于订单金额减去优惠金额）
        BigDecimal taxRate = config.getTaxRate();
        if (taxRate == null || taxRate.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal taxableAmount = subtotal.subtract(discountAmount);
        if (taxableAmount.compareTo(BigDecimal.ZERO) < 0) {
            taxableAmount = BigDecimal.ZERO;
        }

        BigDecimal tax = taxableAmount.multiply(taxRate).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

        log.debug("计算税费，商户ID: {}, 订单金额: {}, 优惠金额: {}, 税率: {}%, 税费: {}", 
                merchantId, subtotal, discountAmount, taxRate, tax);
        return tax.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 计算在线服务费（其他费用）
     */
    public BigDecimal calculateOnlineServiceFee(Order order, BigDecimal subtotal) {
        String merchantId = order.getMerchantId();

        // 获取商户费用配置
        MerchantFeeConfig config = merchantFeeConfigMapper.selectByMerchantId(merchantId);
        if (config == null) {
            log.debug("商户 {} 未配置费用，在线服务费为 0", merchantId);
            return BigDecimal.ZERO;
        }

        String feeType = config.getOnlineServiceFeeType();
        if (feeType == null || "NONE".equalsIgnoreCase(feeType)) {
            return BigDecimal.ZERO;
        }

        BigDecimal serviceFee = BigDecimal.ZERO;

        // 检查是否有阶梯费率策略
        String strategyJson = config.getOnlineServiceFeeStrategy();
        if (strategyJson != null && !strategyJson.isEmpty()) {
            try {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> strategies = objectMapper.readValue(strategyJson, List.class);
                if (strategies != null && !strategies.isEmpty()) {
                    // 找到匹配的策略
                    for (Map<String, Object> strategy : strategies) {
                        Object fromObj = strategy.get("from");
                        Object toObj = strategy.get("to");
                        Object typeObj = strategy.get("type");
                        Object feeObj = strategy.get("fee");

                        if (fromObj != null && toObj != null && typeObj != null && feeObj != null) {
                            double from = Double.parseDouble(fromObj.toString());
                            double to = Double.parseDouble(toObj.toString());
                            String type = typeObj.toString();
                            double fee = Double.parseDouble(feeObj.toString());

                            if (subtotal.doubleValue() >= from && subtotal.doubleValue() < to) {
                                if ("PERCENTAGE".equalsIgnoreCase(type)) {
                                    serviceFee = subtotal.multiply(BigDecimal.valueOf(fee))
                                            .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                                } else if ("FLAT_FEE".equalsIgnoreCase(type)) {
                                    serviceFee = BigDecimal.valueOf(fee);
                                }
                                break;
                            }
                        }
                    }
                    // 如果订单金额超过最后一个策略的 to，使用最后一个策略
                    if (serviceFee.compareTo(BigDecimal.ZERO) == 0 && !strategies.isEmpty()) {
                        Map<String, Object> lastStrategy = strategies.get(strategies.size() - 1);
                        Object toObj = lastStrategy.get("to");
                        if (toObj != null) {
                            double to = Double.parseDouble(toObj.toString());
                            if (subtotal.doubleValue() >= to) {
                                Object typeObj = lastStrategy.get("type");
                                Object feeObj = lastStrategy.get("fee");
                                if (typeObj != null && feeObj != null) {
                                    String type = typeObj.toString();
                                    double fee = Double.parseDouble(feeObj.toString());
                                    if ("PERCENTAGE".equalsIgnoreCase(type)) {
                                        serviceFee = subtotal.multiply(BigDecimal.valueOf(fee))
                                                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                                    } else if ("FLAT_FEE".equalsIgnoreCase(type)) {
                                        serviceFee = BigDecimal.valueOf(fee);
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("解析在线服务费策略失败，商户ID: {}, 策略JSON: {}", merchantId, strategyJson, e);
            }
        }

        // 如果没有策略或策略未匹配，使用固定或百分比费率
        if (serviceFee.compareTo(BigDecimal.ZERO) == 0) {
            if ("PERCENTAGE".equalsIgnoreCase(feeType)) {
                BigDecimal percentage = config.getOnlineServiceFeePercentage();
                if (percentage != null && percentage.compareTo(BigDecimal.ZERO) > 0) {
                    serviceFee = subtotal.multiply(percentage).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                }
            } else if ("FIXED".equalsIgnoreCase(feeType)) {
                BigDecimal fixed = config.getOnlineServiceFeeFixed();
                if (fixed != null && fixed.compareTo(BigDecimal.ZERO) > 0) {
                    serviceFee = fixed;
                }
            }
        }

        log.debug("计算在线服务费，商户ID: {}, 订单金额: {}, 服务费: {}", merchantId, subtotal, serviceFee);
        return serviceFee.setScale(2, RoundingMode.HALF_UP);
    }
}

