package com.jiaoyi.order.service;

import com.jiaoyi.order.config.StripeConfig;
import com.jiaoyi.order.dto.PaymentResponse;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.stripe.model.AccountLink;
import com.stripe.model.PaymentIntent;
import com.stripe.model.PaymentMethod;
import com.stripe.param.AccountCreateParams;
import com.stripe.param.AccountLinkCreateParams;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.PaymentIntentConfirmParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Stripe 支付服务
 * 参照 OO 项目的 StripeService 实现
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StripeService {
    
    private final StripeConfig stripeConfig;
    
    @PostConstruct
    public void init() {
        if (stripeConfig.getEnabled() && stripeConfig.getSecretKey() != null) {
            Stripe.apiKey = stripeConfig.getSecretKey();
            log.info("Stripe 服务初始化成功");
        } else {
            log.warn("Stripe 服务未启用或配置缺失");
        }
    }
    
    /**
     * 创建 Payment Intent（异步支付）
     * 参照 OO 项目的 asynStripePaymentProcess
     * 支持 Stripe Connect（如果提供了 onBehalfOf）
     * 
     * @param orderId 订单ID
     * @param amount 支付金额（元）
     * @param currency 货币代码（默认 USD）
     * @param paymentMethodId 支付方式ID（可选，用于保存的卡片）
     * @param metadata 元数据（包含订单信息）
     * @param onBehalfOf Stripe Connected Account ID（可选，用于 Stripe Connect）
     * @param applicationFeeAmount 平台手续费金额（分，可选，用于 Stripe Connect）
     * @return PaymentResponse 包含 clientSecret 和 paymentIntentId
     */
    public PaymentResponse createPaymentIntent(
            Long orderId,
            BigDecimal amount,
            String currency,
            String paymentMethodId,
            Map<String, String> metadata,
            String onBehalfOf,
            Long applicationFeeAmount) {
        
        log.info("创建 Stripe Payment Intent，订单ID: {}, 金额: {}, 货币: {}, onBehalfOf: {}, applicationFee: {}", 
                orderId, amount, currency, onBehalfOf, applicationFeeAmount);
        
        if (!stripeConfig.getEnabled()) {
            throw new RuntimeException("Stripe 支付未启用");
        }
        
        try {
            // 将金额转换为分（Stripe 使用最小货币单位）
            long amountInCents = amount.multiply(BigDecimal.valueOf(100)).longValue();
            
            // 构建 Payment Intent 参数
            PaymentIntentCreateParams.Builder paramsBuilder = PaymentIntentCreateParams.builder()
                    .setAmount(amountInCents)
                    .setCurrency(currency != null ? currency.toLowerCase() : "usd")
                    .setAutomaticPaymentMethods(
                            PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                    .setEnabled(true)
                                    .build()
                    )
                    .putMetadata("orderId", String.valueOf(orderId));
            
            // 添加其他元数据
            if (metadata != null) {
                metadata.forEach(paramsBuilder::putMetadata);
            }
            
            // 如果提供了 paymentMethodId，则附加到 Payment Intent
            if (paymentMethodId != null && !paymentMethodId.isEmpty()) {
                paramsBuilder.setPaymentMethod(paymentMethodId);
            }
            // Stripe Connect: 如果提供了 onBehalfOf，使用 Direct Charges 模式
            if (onBehalfOf != null && !onBehalfOf.isEmpty()) {
                paramsBuilder.setOnBehalfOf(onBehalfOf);
                paramsBuilder.setTransferData(
                        PaymentIntentCreateParams.TransferData.builder()
                                .setDestination(onBehalfOf)
                                .build()
                );
                
                // 如果提供了平台手续费，设置 application_fee_amount
                if (applicationFeeAmount != null && applicationFeeAmount > 0) {
                    paramsBuilder.setApplicationFeeAmount(applicationFeeAmount);
                    log.info("使用 Stripe Connect，商户账户: {}, 平台手续费: {} 分", onBehalfOf, applicationFeeAmount);
                }
            }
            
            PaymentIntentCreateParams params = paramsBuilder.build();
            
            // 创建 Payment Intent
            PaymentIntent paymentIntent = PaymentIntent.create(params);
            
            // 验证 Payment Intent 创建成功
            if (paymentIntent == null) {
                log.error("Payment Intent 创建失败，返回 null，订单ID: {}", orderId);
                throw new RuntimeException("Stripe Payment Intent 创建失败：返回 null");
            }
            
            String paymentIntentId = paymentIntent.getId();
            String clientSecret = paymentIntent.getClientSecret();
            
            if (paymentIntentId == null || paymentIntentId.isEmpty()) {
                log.error("Payment Intent ID 为空，订单ID: {}", orderId);
                throw new RuntimeException("Stripe Payment Intent 创建失败：ID 为空");
            }
            
            if (clientSecret == null || clientSecret.isEmpty()) {
                log.error("Payment Intent Client Secret 为空，订单ID: {}, Payment Intent ID: {}", orderId, paymentIntentId);
                throw new RuntimeException("Stripe Payment Intent 创建失败：Client Secret 为空");
            }
            
            log.info("Payment Intent 创建成功，ID: {}, Client Secret: {}", paymentIntentId, clientSecret);
            
            // 构建响应
            PaymentResponse response = new PaymentResponse();
            response.setPaymentNo(paymentIntentId);
            response.setStatus("PENDING");
            response.setPaymentMethod("CREDIT_CARD");
            response.setAmount(amount);
            response.setClientSecret(clientSecret);
            response.setPaymentIntentId(paymentIntentId);
            response.setRemark("Stripe Payment Intent 创建成功，请在前端使用 clientSecret 确认支付");
            
            return response;
            
        } catch (StripeException e) {
            log.error("创建 Payment Intent 失败，订单ID: {}", orderId, e);
            throw new RuntimeException("Stripe 支付创建失败: " + e.getMessage());
        }
    }
    
    /**
     * 创建 Payment Intent（兼容旧接口，不支持 Connect）
     */
    public PaymentResponse createPaymentIntent(
            Long orderId,
            BigDecimal amount,
            String currency,
            String paymentMethodId,
            Map<String, String> metadata) {
        return createPaymentIntent(orderId, amount, currency, paymentMethodId, metadata, null, null);
    }
    
    /**
     * 确认 Payment Intent（前端确认支付后调用）
     * 
     * @param paymentIntentId Payment Intent ID
     * @param paymentMethodId 支付方式ID（可选）
     * @return PaymentIntent 确认后的 Payment Intent
     */
    public PaymentIntent confirmPaymentIntent(String paymentIntentId, String paymentMethodId) {
        log.info("确认 Payment Intent，ID: {}, Payment Method: {}", paymentIntentId, paymentMethodId);
        
        try {
            PaymentIntent paymentIntent = PaymentIntent.retrieve(paymentIntentId);
            
            PaymentIntentConfirmParams.Builder paramsBuilder = PaymentIntentConfirmParams.builder();
            
            if (paymentMethodId != null && !paymentMethodId.isEmpty()) {
                paramsBuilder.setPaymentMethod(paymentMethodId);
            }
            
            paymentIntent = paymentIntent.confirm(paramsBuilder.build());
            
            log.info("Payment Intent 确认成功，ID: {}, 状态: {}", paymentIntentId, paymentIntent.getStatus());
            
            return paymentIntent;
            
        } catch (StripeException e) {
            log.error("确认 Payment Intent 失败，ID: {}", paymentIntentId, e);
            throw new RuntimeException("Stripe 支付确认失败: " + e.getMessage());
        }
    }
    
    /**
     * 查询 Payment Intent
     * 
     * @param paymentIntentId Payment Intent ID
     * @return PaymentIntent
     */
    public PaymentIntent retrievePaymentIntent(String paymentIntentId) {
        log.info("查询 Payment Intent，ID: {}", paymentIntentId);
        
        try {
            return PaymentIntent.retrieve(paymentIntentId);
        } catch (StripeException e) {
            log.error("查询 Payment Intent 失败，ID: {}", paymentIntentId, e);
            throw new RuntimeException("查询 Payment Intent 失败: " + e.getMessage());
        }
    }
    
    /**
     * 查询 Payment Method（用于获取卡片信息）
     * 
     * @param paymentMethodId Payment Method ID
     * @return PaymentMethod
     */
    public PaymentMethod retrievePaymentMethod(String paymentMethodId) {
        log.info("查询 Payment Method，ID: {}", paymentMethodId);
        
        try {
            return PaymentMethod.retrieve(paymentMethodId);
        } catch (StripeException e) {
            log.error("查询 Payment Method 失败，ID: {}", paymentMethodId, e);
            throw new RuntimeException("查询 Payment Method 失败: " + e.getMessage());
        }
    }
    
    /**
     * 创建 Payment Method（用于测试卡号）
     * 注意：此方法仅用于测试环境，生产环境应通过 Stripe Elements 在前端创建
     * 
     * @param cardNumber 卡号
     * @param expMonth 过期月份（1-12）
     * @param expYear 过期年份（如 2025）
     * @param cvc CVC 码
     * @param postalCode 邮政编码
     * @param cardholderName 持卡人姓名
     * @return PaymentMethod ID
     */
    public String createPaymentMethodFromCard(
            String cardNumber,
            Integer expMonth,
            Integer expYear,
            String cvc,
            String postalCode,
            String cardholderName) {
        
        log.info("创建 Payment Method（测试模式），卡号: {}****, 过期: {}/{}", 
                cardNumber != null && cardNumber.length() > 4 ? cardNumber.substring(0, 4) : "****", 
                expMonth, expYear);
        
        if (!stripeConfig.getEnabled()) {
            throw new RuntimeException("Stripe 支付未启用");
        }
        
        try {
            // 构建 Payment Method 创建参数
            com.stripe.param.PaymentMethodCreateParams.Builder paramsBuilder = 
                    com.stripe.param.PaymentMethodCreateParams.builder()
                            .setType(com.stripe.param.PaymentMethodCreateParams.Type.CARD);
            
            // 设置卡片信息（使用 CardDetails）
            com.stripe.param.PaymentMethodCreateParams.CardDetails.Builder cardBuilder = 
                    com.stripe.param.PaymentMethodCreateParams.CardDetails.builder();
            
            if (cardNumber != null && !cardNumber.isEmpty()) {
                cardBuilder.setNumber(cardNumber.replaceAll("\\s", "")); // 移除空格
            }
            if (expMonth != null) {
                cardBuilder.setExpMonth(Long.valueOf(expMonth));
            }
            if (expYear != null) {
                cardBuilder.setExpYear(Long.valueOf(expYear));
            }
            if (cvc != null && !cvc.isEmpty()) {
                cardBuilder.setCvc(cvc);
            }
            
            paramsBuilder.setCard(cardBuilder.build());
            
            // 设置账单信息
            if (cardholderName != null && !cardholderName.isEmpty()) {
                com.stripe.param.PaymentMethodCreateParams.BillingDetails.Builder billingBuilder = 
                        com.stripe.param.PaymentMethodCreateParams.BillingDetails.builder()
                                .setName(cardholderName);
                
                if (postalCode != null && !postalCode.isEmpty()) {
                    billingBuilder.setAddress(
                            com.stripe.param.PaymentMethodCreateParams.BillingDetails.Address.builder()
                                    .setPostalCode(postalCode)
                                    .build()
                    );
                }
                
                paramsBuilder.setBillingDetails(billingBuilder.build());
            }
            
            PaymentMethod paymentMethod = PaymentMethod.create(paramsBuilder.build());
            
            log.info("Payment Method 创建成功，ID: {}", paymentMethod.getId());
            
            return paymentMethod.getId();
            
        } catch (StripeException e) {
            log.error("创建 Payment Method 失败", e);
            throw new RuntimeException("创建 Payment Method 失败: " + e.getMessage());
        }
    }
    
    /**
     * 创建 Charge（同步支付，已废弃，推荐使用 Payment Intent）
     * 
     * @param amount 金额（分）
     * @param currency 货币代码
     * @param paymentMethodId 支付方式ID
     * @param metadata 元数据
     * @return Charge
     */
    @Deprecated
    public com.stripe.model.Charge createCharge(
            long amount,
            String currency,
            String paymentMethodId,
            Map<String, String> metadata) {
        
        log.warn("使用已废弃的 createCharge 方法，建议使用 Payment Intent");
        
        // 注意：Stripe 新版本推荐使用 Payment Intent，Charge 创建方式已改变
        // 这里保留方法签名但实际不推荐使用
        throw new UnsupportedOperationException("Charge 创建已废弃，请使用 Payment Intent");
    }
    
    /**
     * 为商户创建 Stripe Connected Account（参照 OO 项目的 createAccount）
     * 
     * @param merchantId 商户ID
     * @param email 商户邮箱（可选）
     * @param country 国家代码（如 "US", "CA"）
     * @return Stripe Account 对象，包含 stripeAccountId（acct_xxx）
     */
    public Account createConnectedAccount(String merchantId, String email, String country) {
        log.info("为商户创建 Stripe Connected Account，商户ID: {}, 邮箱: {}, 国家: {}", merchantId, email, country);
        
        if (!stripeConfig.getEnabled()) {
            throw new RuntimeException("Stripe 支付未启用");
        }
        
        try {
            AccountCreateParams.Builder paramsBuilder = AccountCreateParams.builder()
                    .setType(AccountCreateParams.Type.EXPRESS) // Express 账户，商户可以快速完成验证
                    .setCountry(country != null ? country : "US")
                    .putMetadata("merchantId", merchantId);
            
            if (email != null && !email.isEmpty()) {
                paramsBuilder.setEmail(email);
            }
            
            Account account = Account.create(paramsBuilder.build());
            
            log.info("Stripe Connected Account 创建成功，商户ID: {}, Account ID: {}", merchantId, account.getId());
            
            return account;
            
        } catch (StripeException e) {
            log.error("创建 Stripe Connected Account 失败，商户ID: {}", merchantId, e);
            throw new RuntimeException("创建 Stripe Connected Account 失败: " + e.getMessage());
        }
    }
    
    /**
     * 创建 Account Link，用于商户完成账户验证（参照 OO 项目的 createAccountLinks）
     * 
     * @param accountId Stripe Connected Account ID
     * @param refreshUrl 刷新URL（商户完成验证后跳转）
     * @param returnUrl 返回URL（商户完成验证后跳转）
     * @return AccountLink 对象，包含 url 字段，商户需要访问此 URL 完成验证
     */
    public AccountLink createAccountLink(String accountId, String refreshUrl, String returnUrl) {
        log.info("创建 Account Link，Account ID: {}, refreshUrl: {}, returnUrl: {}", accountId, refreshUrl, returnUrl);
        
        if (!stripeConfig.getEnabled()) {
            throw new RuntimeException("Stripe 支付未启用");
        }
        
        try {
            AccountLinkCreateParams params = AccountLinkCreateParams.builder()
                    .setAccount(accountId)
                    .setRefreshUrl(refreshUrl)
                    .setReturnUrl(returnUrl)
                    .setType(AccountLinkCreateParams.Type.ACCOUNT_ONBOARDING) // 账户入驻
                    .build();
            
            AccountLink accountLink = AccountLink.create(params);
            
            log.info("Account Link 创建成功，URL: {}", accountLink.getUrl());
            
            return accountLink;
            
        } catch (StripeException e) {
            log.error("创建 Account Link 失败，Account ID: {}", accountId, e);
            throw new RuntimeException("创建 Account Link 失败: " + e.getMessage());
        }
    }
    
    /**
     * 查询 Stripe Connected Account 信息
     * 
     * @param accountId Stripe Connected Account ID
     * @return Account 对象
     */
    public Account retrieveAccount(String accountId) {
        log.info("查询 Stripe Account，ID: {}", accountId);
        
        try {
            return Account.retrieve(accountId);
        } catch (StripeException e) {
            log.error("查询 Stripe Account 失败，ID: {}", accountId, e);
            throw new RuntimeException("查询 Stripe Account 失败: " + e.getMessage());
        }
    }
    
    /**
     * 创建退款（参照 OO 项目的退款实现）
     * 
     * @param paymentIntentId Payment Intent ID 或 Charge ID
     * @param amount 退款金额（元），如果为 null 则全额退款
     * @param reason 退款原因
     * @return Refund 对象
     */
    public com.stripe.model.Refund createRefund(String paymentIntentId, BigDecimal amount, String reason) {
        log.info("创建 Stripe 退款，Payment Intent ID: {}, 退款金额: {}, 退款原因: {}", 
                paymentIntentId, amount, reason);
        
        if (!stripeConfig.getEnabled()) {
            throw new RuntimeException("Stripe 支付未启用");
        }
        
        try {
            // 先获取 Payment Intent 以获取 Charge ID
            PaymentIntent paymentIntent = PaymentIntent.retrieve(paymentIntentId);
            String chargeId = paymentIntent.getLatestCharge();
            
            if (chargeId == null || chargeId.isEmpty()) {
                log.error("Payment Intent 没有关联的 Charge，无法退款，Payment Intent ID: {}", paymentIntentId);
                throw new RuntimeException("Payment Intent 没有关联的 Charge，无法退款");
            }
            
            // 构建退款参数
            com.stripe.param.RefundCreateParams.Builder paramsBuilder = 
                    com.stripe.param.RefundCreateParams.builder()
                            .setCharge(chargeId)
                            .putMetadata("reason", reason != null ? reason : "订单超时自动退款");
            
            // 如果指定了退款金额，则部分退款
            if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
                long amountInCents = amount.multiply(BigDecimal.valueOf(100)).longValue();
                paramsBuilder.setAmount(amountInCents);
                log.info("部分退款，金额: {} 分", amountInCents);
            } else {
                log.info("全额退款");
            }
            
            com.stripe.model.Refund refund = com.stripe.model.Refund.create(paramsBuilder.build());
            
            log.info("Stripe 退款创建成功，Refund ID: {}, Charge ID: {}", refund.getId(), chargeId);
            
            return refund;
            
        } catch (StripeException e) {
            log.error("创建 Stripe 退款失败，Payment Intent ID: {}", paymentIntentId, e);
            throw new RuntimeException("创建 Stripe 退款失败: " + e.getMessage());
        }
    }
}

