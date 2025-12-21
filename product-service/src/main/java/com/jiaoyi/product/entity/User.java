package com.jiaoyi.product.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户实体类
 * 对应 online-order-v2-backend 的 User 模型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {
    
    /**
     * 主键ID（自增，不分片）
     */
    private Long id;
    
    /**
     * 邮箱
     */
    private String email;
    
    /**
     * 手机号
     */
    private String phone;
    
    /**
     * 国家代码
     */
    private String countryCode;
    
    /**
     * 姓名
     */
    private String name;
    
    /**
     * 密码（加密）
     */
    private String password;
    
    /**
     * 头像URL
     */
    private String avatarUrl;
    
    /**
     * 用户状态：100-新用户，200-活跃，666-禁用
     */
    private Integer status;
    
    /**
     * 配送地址（JSON格式）
     */
    private String deliveryAddress;
    
    /**
     * Stripe客户ID
     */
    private String stripeCustomerId;
    
    /**
     * 微信OpenID
     */
    private String openid;
    
    /**
     * 微信UnionID
     */
    private String unionid;
    
    /**
     * 微信头像URL
     */
    private String headImgUrl;
    
    /**
     * 注册渠道
     */
    private String registChannel;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}

