package com.jiaoyi.order.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Google Maps 服务
 * 用于计算配送距离和地理编码
 * 参照 OO 项目的 GoogleMapsServer
 */
@Service
@Slf4j
public class GoogleMapsService {

    /**
     * 地球半径（公里）
     */
    private static final double EARTH_RADIUS_KM = 6378.137;
    
    /**
     * 英里转米的系数
     */
    private static final double MILE_TO_METER = 1609.344;

    /**
     * 根据经纬度计算两点之间的距离（米）
     * 使用 Haversine 公式
     * 
     * @param originLatitude 起点纬度
     * @param originLongitude 起点经度
     * @param destinationLatitude 终点纬度
     * @param destinationLongitude 终点经度
     * @return 距离（米）
     */
    public double calculateDistance(double originLatitude, double originLongitude,
                                    double destinationLatitude, double destinationLongitude) {
        double lat1Rad = Math.toRadians(originLatitude);
        double lat2Rad = Math.toRadians(destinationLatitude);
        double deltaLatRad = Math.toRadians(destinationLatitude - originLatitude);
        double deltaLonRad = Math.toRadians(destinationLongitude - originLongitude);

        double a = Math.sin(deltaLatRad / 2) * Math.sin(deltaLatRad / 2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                Math.sin(deltaLonRad / 2) * Math.sin(deltaLonRad / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        double distanceKm = EARTH_RADIUS_KM * c;
        double distanceMeter = distanceKm * 1000;

        log.debug("计算距离，起点: ({}, {}), 终点: ({}, {}), 距离: {} 米", 
                originLatitude, originLongitude, destinationLatitude, destinationLongitude, distanceMeter);
        
        return distanceMeter;
    }

    /**
     * 英里转米
     */
    public double mileToMeter(double mile) {
        return mile * MILE_TO_METER;
    }

    /**
     * 米转英里
     */
    public double meterToMile(double meter) {
        return meter / MILE_TO_METER;
    }

    /**
     * 地理编码：根据地址获取经纬度
     * TODO: 集成 Google Maps Geocoding API
     * 目前返回 null，需要实际集成 API
     * 
     * @param formattedAddress 格式化地址
     * @param latitude 已知纬度（如果地址解析失败，使用此值）
     * @param longitude 已知经度（如果地址解析失败，使用此值）
     * @return 经纬度对象，包含 destinationLatitude 和 destinationLongitude
     */
    public GeocodeResult getGeocode(String formattedAddress, Double latitude, Double longitude) {
        // TODO: 集成 Google Maps Geocoding API
        // 目前返回原始坐标（如果提供）
        log.warn("Google Maps Geocoding API 未集成，使用提供的坐标");
        if (latitude != null && longitude != null) {
            return new GeocodeResult(latitude, longitude);
        }
        return null;
    }

    /**
     * 地理编码结果
     */
    public static class GeocodeResult {
        private final double destinationLatitude;
        private final double destinationLongitude;

        public GeocodeResult(double destinationLatitude, double destinationLongitude) {
            this.destinationLatitude = destinationLatitude;
            this.destinationLongitude = destinationLongitude;
        }

        public double getDestinationLatitude() {
            return destinationLatitude;
        }

        public double getDestinationLongitude() {
            return destinationLongitude;
        }
    }
}

