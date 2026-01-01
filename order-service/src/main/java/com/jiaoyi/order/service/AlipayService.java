package com.jiaoyi.order.service;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.domain.AlipayTradePrecreateModel;
import com.alipay.api.domain.AlipayTradeQueryModel;
import com.alipay.api.domain.AlipayTradeRefundModel;
import com.alipay.api.request.AlipayTradePrecreateRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradePrecreateResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.jiaoyi.order.config.AlipayConfig;
import com.jiaoyi.order.dto.PaymentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付宝支付服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlipayService {
    
    private final AlipayConfig alipayConfig;
    private AlipayClient alipayClient;
    
    /**
     * 获取AlipayClient实例
     */
    private AlipayClient getAlipayClient() {
        if (alipayClient == null) {
            alipayClient = new DefaultAlipayClient(
                alipayConfig.getGatewayUrl(),
                alipayConfig.getAppId(),
                alipayConfig.getPrivateKey(),
                "json",
                "UTF-8",
                alipayConfig.getAlipayPublicKey(),
                "RSA2"
            );
        }
        return alipayClient;
    }
    
    /**
     * 创建支付宝支付订单 (真实支付)
     */
    public PaymentResponse createPayment(String orderNo, String subject, BigDecimal amount, String paymentId) {
        log.info("创建真实支付宝支付订单，订单号: {}, 金额: {}", orderNo, amount);
        
        try {
            // 初始化支付宝客户端
            AlipayClient alipayClient = new DefaultAlipayClient(
                alipayConfig.getGatewayUrl(),
                alipayConfig.getAppId(),
                alipayConfig.getPrivateKey(),
                "json",
                "UTF-8",
                alipayConfig.getAlipayPublicKey(),
                "RSA2"
            );
            
            // 创建预下单请求
            AlipayTradePrecreateRequest request = new AlipayTradePrecreateRequest();
            request.setNotifyUrl(alipayConfig.getNotifyUrl());
            request.setReturnUrl(alipayConfig.getReturnUrl());
            
            // 设置业务参数
            AlipayTradePrecreateModel model = new AlipayTradePrecreateModel();
            model.setOutTradeNo(orderNo); // 商户订单号
            model.setTotalAmount(amount.toString()); // 订单总金额
            model.setSubject(subject); // 订单标题
            model.setBody("商品购买"); // 订单描述
            model.setTimeoutExpress("30m"); // 订单超时时间
            
            request.setBizModel(model);
            
            // 调用支付宝API
            log.info("调用支付宝预下单API，订单号: {}, 金额: {}, 网关: {}", orderNo, amount, alipayConfig.getGatewayUrl());
            AlipayTradePrecreateResponse response = alipayClient.execute(request);
            
            log.info("支付宝API响应 - 成功: {}, 错误码: {}, 错误信息: {}, 二维码: {}", 
                    response.isSuccess(), response.getCode(), response.getMsg(), response.getQrCode());
            
            if (response.isSuccess()) {
                String qrCode = response.getQrCode();
                if (qrCode == null || qrCode.trim().isEmpty()) {
                    log.error("支付宝预下单成功但二维码为空，订单号: {}, 错误码: {}, 错误信息: {}, 子错误码: {}, 子错误信息: {}", 
                            orderNo, response.getCode(), response.getMsg(), response.getSubCode(), response.getSubMsg());
                    throw new RuntimeException("支付宝预下单成功但无法获取二维码，请检查支付宝配置或联系客服");
                }
                
                log.info("支付宝预下单成功，订单号: {}, 二维码: {}", orderNo, qrCode);
                
                PaymentResponse paymentResponse = new PaymentResponse();
                if (paymentId != null) {
                    try {
                        paymentResponse.setPaymentId(Long.parseLong(paymentId));
                    } catch (NumberFormatException e) {
                        log.warn("支付ID格式错误: {}", paymentId);
                    }
                }
                paymentResponse.setPaymentMethod("ALIPAY");
                paymentResponse.setAmount(amount);
                paymentResponse.setStatus("PENDING");
                paymentResponse.setQrCode(qrCode); // 设置二维码
                paymentResponse.setPayUrl(qrCode); // 设置支付链接（二维码URL）
                paymentResponse.setRemark("支付宝支付订单创建成功");
                
                return paymentResponse;
            } else {
                log.error("支付宝预下单失败，错误码: {}, 错误信息: {}, 子错误码: {}, 子错误信息: {}", 
                        response.getCode(), response.getMsg(), response.getSubCode(), response.getSubMsg());
                throw new RuntimeException("支付宝预下单失败: " + response.getMsg() + 
                        (response.getSubMsg() != null ? " - " + response.getSubMsg() : ""));
            }
            
        } catch (AlipayApiException e) {
            log.error("调用支付宝API异常，订单号: {}", orderNo, e);
            throw new RuntimeException("支付宝支付创建失败: " + e.getMessage());
        }
    }
    
    /**
     * 查询支付结果 (真实支付查询)
     */
    public PaymentResponse queryPayment(String orderNo) {
        log.info("查询真实支付宝支付结果，订单号: {}", orderNo);
        
        try {
            // 初始化支付宝客户端
            AlipayClient alipayClient = new DefaultAlipayClient(
                alipayConfig.getGatewayUrl(),
                alipayConfig.getAppId(),
                alipayConfig.getPrivateKey(),
                "json",
                "UTF-8",
                alipayConfig.getAlipayPublicKey(),
                "RSA2"
            );
            
            // 创建查询请求
            AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
            AlipayTradeQueryModel model = new AlipayTradeQueryModel();
            model.setOutTradeNo(orderNo);
            request.setBizModel(model);
            // 调用支付宝查询API
            log.info("调用支付宝查询API，订单号: {}, 网关: {}", orderNo, alipayConfig.getGatewayUrl());
            AlipayTradeQueryResponse response = alipayClient.execute(request);
            
            log.info("支付宝查询响应 - 成功: {}, 错误码: {}, 错误信息: {}, 交易状态: {}", 
                    response.isSuccess(), response.getCode(), response.getMsg(), response.getTradeStatus());
            
            if (response.isSuccess()) {
                log.info("支付宝支付查询成功，订单号: {}, 交易状态: {}, 支付宝交易号: {}", 
                        orderNo, response.getTradeStatus(), response.getTradeNo());
                
                PaymentResponse paymentResponse = new PaymentResponse();
                paymentResponse.setPaymentMethod("ALIPAY");
                paymentResponse.setStatus(mapTradeStatus(response.getTradeStatus()));
                paymentResponse.setThirdPartyTradeNo(response.getTradeNo());
                paymentResponse.setRemark("支付宝支付查询成功");
                
                return paymentResponse;
            } else {
                log.error("支付宝支付查询失败，错误码: {}, 错误信息: {}, 子错误码: {}, 子错误信息: {}", 
                        response.getCode(), response.getMsg(), response.getSubCode(), response.getSubMsg());
                throw new RuntimeException("支付宝支付查询失败: " + response.getMsg() + " - " + response.getSubMsg());
            }
            
        } catch (AlipayApiException e) {
            log.error("调用支付宝查询API异常，订单号: {}", orderNo, e);
            throw new RuntimeException("支付宝支付查询失败: " + e.getMessage());
        }
    }
    
    /**
     * 映射支付宝交易状态到系统状态
     */
    private String mapTradeStatus(String tradeStatus) {
        switch (tradeStatus) {
            case "TRADE_SUCCESS":
            case "TRADE_FINISHED":
                return "SUCCESS";
            case "TRADE_CLOSED":
                return "FAILED";
            case "WAIT_BUYER_PAY":
            default:
                return "PENDING";
        }
    }
    
    /**
     * 发起退款
     * @param outTradeNo 商户订单号
     * @param refundAmount 退款金额
     * @param refundReason 退款原因
     * @return 退款是否成功
     */
    public boolean refund(String outTradeNo, BigDecimal refundAmount, String refundReason) {
        log.info("发起支付宝退款，订单号: {}, 退款金额: {}, 退款原因: {}", outTradeNo, refundAmount, refundReason);
        
        try {
            // 生成退款单号
            String refundNo = "REF" + System.currentTimeMillis() + (int)(Math.random() * 1000);
            
            // 构建退款请求
            AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
            AlipayTradeRefundModel model = new AlipayTradeRefundModel();
            model.setOutTradeNo(outTradeNo);
            model.setRefundAmount(refundAmount.toString());
            model.setRefundReason(refundReason);
            model.setOutRequestNo(refundNo);
            request.setBizModel(model);
            
            // 调用退款接口
            AlipayTradeRefundResponse response = getAlipayClient().execute(request);
            
            if (response.isSuccess()) {
                log.info("支付宝退款成功，订单号: {}, 退款单号: {}, 退款金额: {}", 
                        outTradeNo, refundNo, refundAmount);
                return true;
            } else {
                log.error("支付宝退款失败，订单号: {}, 错误码: {}, 错误信息: {}", 
                        outTradeNo, response.getCode(), response.getMsg());
                return false;
            }
            
        } catch (Exception e) {
            log.error("支付宝退款异常，订单号: {}", outTradeNo, e);
            return false;
        }
    }
}


