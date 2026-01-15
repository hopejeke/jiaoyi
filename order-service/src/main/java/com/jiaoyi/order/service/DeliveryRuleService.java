package com.jiaoyi.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiaoyi.common.exception.BusinessException;
import com.jiaoyi.order.entity.MerchantFeeConfig;
import com.jiaoyi.order.enums.DeliveryFeeTypeEnum;
import com.jiaoyi.order.mapper.MerchantFeeConfigMapper;
import com.jiaoyi.order.service.GoogleMapsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 配送规则检查服务
 * 负责检查商家设置的基础规则（距离、时段）
 * 在调用 DoorDash API 之前进行第一层筛选
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryRuleService {
    
    private final MerchantFeeConfigMapper merchantFeeConfigMapper;
    private final GoogleMapsService googleMapsService;
    private final ObjectMapper objectMapper;
    
    /**
     * 检查配送地址是否符合商家基础规则
     * 
     * @param merchantId 商户ID
     * @param customerLatitude 客户地址纬度
     * @param customerLongitude 客户地址经度
     * @param zipCode 客户地址邮编（可选）
     * @param orderTime 订单时间（用于检查配送时段）
     * @throws BusinessException 如果不符合规则
     */
    public void checkDeliveryRules(
            String merchantId,
            Double customerLatitude,
            Double customerLongitude,
            String zipCode,
            java.time.LocalDateTime orderTime) {
        
        log.info("检查配送规则，商户ID: {}, 客户坐标: ({}, {}), 邮编: {}, 订单时间: {}", 
                merchantId, customerLatitude, customerLongitude, zipCode, orderTime);
        
        // 1. 获取商户费用配置
        MerchantFeeConfig config = merchantFeeConfigMapper.selectByMerchantId(merchantId);
        if (config == null) {
            log.warn("商户 {} 未配置费用信息，跳过配送规则检查", merchantId);
            return; // 未配置则跳过检查
        }
        
        // 2. 检查配送时段
        checkDeliveryTimeSlot(config, orderTime);
        
        // 3. 检查配送距离或邮编区域
        DeliveryFeeTypeEnum deliveryFeeType = config.getDeliveryFeeType();
        if (deliveryFeeType == null) {
            deliveryFeeType = DeliveryFeeTypeEnum.FLAT_RATE;
        }
        
        if (DeliveryFeeTypeEnum.ZONE_RATE.equals(deliveryFeeType)) {
            // 按邮编区域检查
            checkZipCodeZone(config, zipCode);
        } else if (DeliveryFeeTypeEnum.VARIABLE_RATE.equals(deliveryFeeType)) {
            // 按距离检查
            checkDeliveryDistance(config, customerLatitude, customerLongitude);
        } else {
            // FLAT_RATE 或其他类型，检查最大配送距离（如果有配置）
            if (config.getDeliveryMaximumDistance() != null && 
                config.getDeliveryMaximumDistance().compareTo(BigDecimal.ZERO) > 0) {
                checkDeliveryDistance(config, customerLatitude, customerLongitude);
            }
        }
        
        log.info("配送规则检查通过，商户ID: {}", merchantId);
    }
    
    /**
     * 检查配送时段
     */
    private void checkDeliveryTimeSlot(MerchantFeeConfig config, java.time.LocalDateTime orderTime) {
        String deliveryTimeSlots = config.getDeliveryTimeSlots();
        if (deliveryTimeSlots == null || deliveryTimeSlots.isEmpty()) {
            // 未配置时段，表示全天可配送
            return;
        }
        
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> timeSlots = objectMapper.readValue(deliveryTimeSlots, Map.class);
            
            // 获取当前星期几
            DayOfWeek dayOfWeek = orderTime.getDayOfWeek();
            String dayKey = dayOfWeek.name().toLowerCase(); // monday, tuesday, etc.
            
            // 检查是否有每日统一配置
            @SuppressWarnings("unchecked")
            Map<String, Object> dailySlot = (Map<String, Object>) timeSlots.get("daily");
            if (dailySlot != null) {
                checkTimeSlot(dailySlot, orderTime.toLocalTime(), dayKey);
                return;
            }
            
            // 检查是否有特定日期的配置
            @SuppressWarnings("unchecked")
            Map<String, Object> daySlot = (Map<String, Object>) timeSlots.get(dayKey);
            if (daySlot != null) {
                checkTimeSlot(daySlot, orderTime.toLocalTime(), dayKey);
                return;
            }
            
            // 如果配置了时段但没有当前日期的配置，默认不允许配送
            log.warn("商户 {} 配置了配送时段，但 {} 未配置，不允许配送", config.getMerchantId(), dayKey);
            throw new BusinessException("当前时段不在配送时间内");
            
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("解析配送时段配置失败，商户ID: {}", config.getMerchantId(), e);
            // 解析失败时，为了安全起见，不允许配送
            throw new BusinessException("配送时段配置错误，无法配送");
        }
    }
    
    /**
     * 检查单个时段配置
     */
    private void checkTimeSlot(Map<String, Object> slot, LocalTime orderTime, String dayKey) {
        String startTimeStr = (String) slot.get("start");
        String endTimeStr = (String) slot.get("end");
        
        if (startTimeStr == null || endTimeStr == null) {
            log.warn("时段配置缺少 start 或 end，day: {}", dayKey);
            throw new BusinessException("配送时段配置不完整");
        }
        
        try {
            LocalTime startTime = LocalTime.parse(startTimeStr, DateTimeFormatter.ofPattern("HH:mm"));
            LocalTime endTime = LocalTime.parse(endTimeStr, DateTimeFormatter.ofPattern("HH:mm"));
            
            // 检查订单时间是否在配送时段内
            if (orderTime.isBefore(startTime) || orderTime.isAfter(endTime)) {
                log.warn("订单时间 {} 不在配送时段内 ({}-{})", orderTime, startTime, endTime);
                throw new BusinessException("当前时段不在配送时间内，配送时间: " + startTimeStr + "-" + endTimeStr);
            }
            
        } catch (Exception e) {
            if (e instanceof BusinessException) {
                throw e;
            }
            log.error("解析时段失败，start: {}, end: {}", startTimeStr, endTimeStr, e);
            throw new BusinessException("配送时段格式错误");
        }
    }
    
    /**
     * 检查邮编区域
     */
    private void checkZipCodeZone(MerchantFeeConfig config, String zipCode) {
        if (zipCode == null || zipCode.isEmpty()) {
            log.warn("订单未提供邮编，无法检查邮编区域");
            throw new BusinessException("配送地址缺少邮编信息");
        }
        
        String deliveryZoneRate = config.getDeliveryZoneRate();
        if (deliveryZoneRate == null || deliveryZoneRate.isEmpty()) {
            // 未配置邮编区域，允许配送
            return;
        }
        
        try {
            // 处理邮编格式（可能包含 "-"，只取前5位）
            String normalizedZipCode = zipCode;
            if (zipCode.contains("-")) {
                normalizedZipCode = zipCode.split("-")[0];
            }
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> zoneRates = objectMapper.readValue(deliveryZoneRate, List.class);
            if (zoneRates == null || zoneRates.isEmpty()) {
                // 配置为空，允许配送
                return;
            }
            
            // 查找匹配的邮编区域
            for (Map<String, Object> zoneRate : zoneRates) {
                @SuppressWarnings("unchecked")
                List<String> zipcodes = (List<String>) zoneRate.get("zipcodes");
                if (zipcodes != null && zipcodes.contains(normalizedZipCode)) {
                    log.debug("邮编 {} 在配送区域内", normalizedZipCode);
                    return; // 找到匹配的区域
                }
            }
            
            // 如果邮编不在任何区域，拒绝配送
            log.warn("邮编 {} 不在任何配送区域内", normalizedZipCode);
            throw new BusinessException("该邮编不在配送范围内");
            
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("解析邮编区域配置失败，商户ID: {}", config.getMerchantId(), e);
            throw new BusinessException("配送区域配置错误");
        }
    }
    
    /**
     * 检查配送距离
     */
    private void checkDeliveryDistance(MerchantFeeConfig config, Double customerLatitude, Double customerLongitude) {
        if (customerLatitude == null || customerLongitude == null) {
            log.warn("订单未提供地址坐标，无法检查配送距离");
            throw new BusinessException("配送地址缺少坐标信息");
        }
        
        BigDecimal merchantLat = config.getMerchantLatitude();
        BigDecimal merchantLon = config.getMerchantLongitude();
        if (merchantLat == null || merchantLon == null) {
            log.warn("商户 {} 未配置坐标，无法检查配送距离", config.getMerchantId());
            throw new BusinessException("商户未配置坐标信息");
        }
        
        // 计算配送距离（米）
        double distanceMeter = googleMapsService.calculateDistance(
                merchantLat.doubleValue(), 
                merchantLon.doubleValue(),
                customerLatitude, 
                customerLongitude
        );
        
        // 检查最大配送距离
        BigDecimal maxDistance = config.getDeliveryMaximumDistance();
        if (maxDistance != null && maxDistance.compareTo(BigDecimal.ZERO) > 0) {
            double maxDistanceMeter = googleMapsService.mileToMeter(maxDistance.doubleValue());
            if (distanceMeter > maxDistanceMeter) {
                log.warn("配送距离 {} 米超过最大配送距离 {} 米", distanceMeter, maxDistanceMeter);
                throw new BusinessException("配送距离超过最大配送范围（" + maxDistance + " 英里）");
            }
        }
        
        log.debug("配送距离检查通过，距离: {} 米", distanceMeter);
    }
}














