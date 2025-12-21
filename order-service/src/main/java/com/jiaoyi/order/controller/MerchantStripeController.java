package com.jiaoyi.order.controller;

import com.jiaoyi.common.ApiResponse;
import com.jiaoyi.order.entity.MerchantStripeConfig;
import com.jiaoyi.order.mapper.MerchantStripeConfigMapper;
import com.jiaoyi.order.service.StripeService;
import com.stripe.model.Account;
import com.stripe.model.AccountLink;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 商户 Stripe 配置控制器
 * 用于创建和管理商户的 Stripe Connected Account
 */
@RestController
@RequestMapping("/api/merchant/stripe")
@RequiredArgsConstructor
@Slf4j
public class MerchantStripeController {
    
    private final StripeService stripeService;
    private final MerchantStripeConfigMapper merchantStripeConfigMapper;
    
    /**
     * 为商户创建 Stripe Connected Account
     * 
     * @param merchantId 商户ID
     * @param email 商户邮箱（可选）
     * @param country 国家代码（如 "US", "CA"，默认 "US"）
     * @return Account ID 和 Account Link URL
     */
    @PostMapping("/create-account/{merchantId}")
    public ResponseEntity<ApiResponse<Map<String, String>>> createConnectedAccount(
            @PathVariable String merchantId,
            @RequestParam(required = false) String email,
            @RequestParam(required = false, defaultValue = "US") String country) {
        
        log.info("为商户创建 Stripe Connected Account，商户ID: {}, 邮箱: {}, 国家: {}", merchantId, email, country);
        
        try {
            // 1. 检查是否已有配置
            MerchantStripeConfig existingConfig = merchantStripeConfigMapper.selectByMerchantId(merchantId);
            if (existingConfig != null && existingConfig.getStripeAccountId() != null 
                    && !existingConfig.getStripeAccountId().isEmpty()) {
                log.warn("商户已有 Stripe Connected Account，商户ID: {}, Account ID: {}", merchantId, existingConfig.getStripeAccountId());
                Map<String, String> result = new HashMap<>();
                result.put("accountId", existingConfig.getStripeAccountId());
                result.put("message", "商户已有 Stripe Connected Account");
                return ResponseEntity.ok(ApiResponse.success("商户已有账户", result));
            }
            
            // 2. 创建 Stripe Connected Account
            Account account = stripeService.createConnectedAccount(merchantId, email, country);
            String accountId = account.getId();
            
            // 3. 创建 Account Link（用于商户完成验证）
            String refreshUrl = "http://localhost:8080/merchant/stripe/refresh?merchantId=" + merchantId;
            String returnUrl = "http://localhost:8080/merchant/stripe/return?merchantId=" + merchantId;
            AccountLink accountLink = stripeService.createAccountLink(accountId, refreshUrl, returnUrl);
            
            // 4. 保存到数据库
            MerchantStripeConfig config = new MerchantStripeConfig();
            config.setMerchantId(merchantId);
            config.setStripeAccountId(accountId);
            config.setEnabled(false); // 初始为未启用，需要商户完成验证后才能启用
            config.setCurrency("USD");
            config.setCreateTime(LocalDateTime.now());
            config.setUpdateTime(LocalDateTime.now());
            
            if (existingConfig != null) {
                config.setId(existingConfig.getId());
                merchantStripeConfigMapper.update(config);
            } else {
                merchantStripeConfigMapper.insert(config);
            }
            
            // 5. 返回结果
            Map<String, String> result = new HashMap<>();
            result.put("accountId", accountId);
            result.put("onboardingUrl", accountLink.getUrl()); // 商户需要访问此 URL 完成验证
            result.put("message", "Stripe Connected Account 创建成功，请访问 onboardingUrl 完成验证");
            
            log.info("Stripe Connected Account 创建成功，商户ID: {}, Account ID: {}", merchantId, accountId);
            
            return ResponseEntity.ok(ApiResponse.success("创建成功", result));
            
        } catch (Exception e) {
            log.error("创建 Stripe Connected Account 失败，商户ID: {}", merchantId, e);
            
            // 检查是否是 Stripe Connect 未启用的错误
            String errorMessage = e.getMessage();
            if (errorMessage != null && errorMessage.contains("signed up for Connect")) {
                String friendlyMessage = "Stripe Connect 功能未启用。\n\n" +
                        "请按以下步骤启用：\n" +
                        "1. 登录 Stripe Dashboard: https://dashboard.stripe.com/\n" +
                        "2. 进入 Settings → Connect\n" +
                        "3. 点击 \"Get started\" 或 \"Enable Connect\"\n" +
                        "4. 完成 Connect 注册流程\n" +
                        "5. 完成后重新尝试创建 Connected Account\n\n" +
                        "详细文档: https://stripe.com/docs/connect";
                return ResponseEntity.ok(ApiResponse.error(400, friendlyMessage));
            }
            
            return ResponseEntity.ok(ApiResponse.error(500, "创建失败: " + errorMessage));
        }
    }
    
    /**
     * 查询商户的 Stripe 配置
     */
    @GetMapping("/config/{merchantId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMerchantConfig(@PathVariable String merchantId) {
        log.info("查询商户 Stripe 配置，商户ID: {}", merchantId);
        
        try {
            MerchantStripeConfig config = merchantStripeConfigMapper.selectByMerchantId(merchantId);
            
            Map<String, Object> result = new HashMap<>();
            
            if (config == null) {
                result.put("hasConfig", false);
                result.put("enabled", false);
                result.put("message", "商户未配置 Stripe Connect");
                result.put("stripeAccountId", null);
                result.put("currency", "USD");
                result.put("applicationFeePercentage", 2.5);
                result.put("applicationFeeFixed", 0.30);
                return ResponseEntity.ok(ApiResponse.success("查询成功（未配置）", result));
            }
            
            result.put("hasConfig", true);
            result.put("merchantId", config.getMerchantId());
            result.put("stripeAccountId", config.getStripeAccountId());
            result.put("enabled", config.getEnabled() != null && config.getEnabled());
            result.put("currency", config.getCurrency() != null ? config.getCurrency() : "USD");
            result.put("applicationFeePercentage", config.getApplicationFeePercentage() != null ? config.getApplicationFeePercentage() : 2.5);
            result.put("applicationFeeFixed", config.getApplicationFeeFixed() != null ? config.getApplicationFeeFixed() : 0.30);
            result.put("amexApplicationFeePercentage", config.getAmexApplicationFeePercentage() != null ? config.getAmexApplicationFeePercentage() : 3.5);
            result.put("amexApplicationFeeFixed", config.getAmexApplicationFeeFixed() != null ? config.getAmexApplicationFeeFixed() : 0.30);
            
            // 如果已配置 Account ID，查询 Stripe 账户状态
            if (config.getStripeAccountId() != null && !config.getStripeAccountId().isEmpty()) {
                try {
                    Account account = stripeService.retrieveAccount(config.getStripeAccountId());
                    result.put("accountChargesEnabled", account.getChargesEnabled());
                    result.put("accountPayoutsEnabled", account.getPayoutsEnabled());
                    result.put("accountType", account.getType());
                    result.put("accountCountry", account.getCountry());
                    result.put("accountEmail", account.getEmail());
                    
                    // 根据 account 的状态更新 config.enabled
                    // 只有当账户可以收款和提现时，才启用
                    if (account.getChargesEnabled() != null && account.getChargesEnabled() 
                            && account.getPayoutsEnabled() != null && account.getPayoutsEnabled()) {
                        if (config.getEnabled() == null || !config.getEnabled()) {
                            config.setEnabled(true);
                            config.setUpdateTime(LocalDateTime.now());
                            merchantStripeConfigMapper.update(config);
                            result.put("enabled", true);
                        }
                    }
                } catch (Exception e) {
                    log.warn("查询 Stripe Account 状态失败，Account ID: {}", config.getStripeAccountId(), e);
                }
            }
            
            return ResponseEntity.ok(ApiResponse.success("查询成功", result));
            
        } catch (Exception e) {
            log.error("查询商户 Stripe 配置失败，商户ID: {}", merchantId, e);
            return ResponseEntity.ok(ApiResponse.error(500, "查询失败: " + e.getMessage()));
        }
    }
    
    /**
     * 更新商户 Stripe 配置（启用/禁用、手续费率等）
     */
    @PutMapping("/config/{merchantId}")
    public ResponseEntity<ApiResponse<String>> updateMerchantConfig(
            @PathVariable String merchantId,
            @RequestBody MerchantStripeConfig config) {
        
        log.info("更新商户 Stripe 配置，商户ID: {}", merchantId);
        
        try {
            config.setMerchantId(merchantId);
            config.setUpdateTime(LocalDateTime.now());
            
            MerchantStripeConfig existing = merchantStripeConfigMapper.selectByMerchantId(merchantId);
            
            // 如果提供了 stripeAccountId，验证账户是否存在
            if (config.getStripeAccountId() != null && !config.getStripeAccountId().isEmpty()) {
                try {
                    Account account = stripeService.retrieveAccount(config.getStripeAccountId());
                    log.info("验证 Stripe Account 成功，Account ID: {}, 可收款: {}, 可提现: {}", 
                            account.getId(), account.getChargesEnabled(), account.getPayoutsEnabled());
                    
                    // 如果账户可以收款和提现，自动启用
                    if (account.getChargesEnabled() != null && account.getChargesEnabled() 
                            && account.getPayoutsEnabled() != null && account.getPayoutsEnabled()) {
                        if (config.getEnabled() == null) {
                            config.setEnabled(true);
                            log.info("账户已验证，自动启用 Stripe Connect");
                        }
                    } else {
                        // 账户未验证，保持未启用状态
                        if (config.getEnabled() == null) {
                            config.setEnabled(false);
                        }
                        log.warn("账户未完成验证，保持未启用状态。Account ID: {}", account.getId());
                    }
                } catch (Exception e) {
                    log.warn("验证 Stripe Account 失败，Account ID: {}", config.getStripeAccountId(), e);
                    // 不阻止配置保存，但保持未启用状态
                    if (config.getEnabled() == null) {
                        config.setEnabled(false);
                    }
                }
            }
            
            if (existing == null) {
                // 创建新配置
                config.setCreateTime(LocalDateTime.now());
                if (config.getCurrency() == null) {
                    config.setCurrency("USD");
                }
                if (config.getApplicationFeePercentage() == null) {
                    config.setApplicationFeePercentage(2.5);
                }
                if (config.getApplicationFeeFixed() == null) {
                    config.setApplicationFeeFixed(0.30);
                }
                merchantStripeConfigMapper.insert(config);
                log.info("创建新商户 Stripe 配置，商户ID: {}", merchantId);
            } else {
                // 更新现有配置
                config.setId(existing.getId());
                // 保留原有值（如果新值未提供）
                if (config.getStripeAccountId() == null || config.getStripeAccountId().isEmpty()) {
                    config.setStripeAccountId(existing.getStripeAccountId());
                }
                if (config.getCurrency() == null) {
                    config.setCurrency(existing.getCurrency());
                }
                if (config.getApplicationFeePercentage() == null) {
                    config.setApplicationFeePercentage(existing.getApplicationFeePercentage());
                }
                if (config.getApplicationFeeFixed() == null) {
                    config.setApplicationFeeFixed(existing.getApplicationFeeFixed());
                }
                if (config.getEnabled() == null) {
                    config.setEnabled(existing.getEnabled());
                }
                merchantStripeConfigMapper.update(config);
                log.info("更新商户 Stripe 配置，商户ID: {}", merchantId);
            }
            
            return ResponseEntity.ok(ApiResponse.success("更新成功", null));
            
        } catch (Exception e) {
            log.error("更新商户 Stripe 配置失败，商户ID: {}", merchantId, e);
            return ResponseEntity.ok(ApiResponse.error(500, "更新失败: " + e.getMessage()));
        }
    }
    
    /**
     * 查询商户的抽成配置和状态
     */
    @GetMapping("/fee-status/{merchantId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getFeeStatus(@PathVariable String merchantId) {
        log.info("查询商户抽成配置和状态，商户ID: {}", merchantId);
        
        try {
            MerchantStripeConfig config = merchantStripeConfigMapper.selectByMerchantId(merchantId);
            
            Map<String, Object> result = new HashMap<>();
            
            if (config == null) {
                result.put("hasConfig", false);
                result.put("enabled", false);
                result.put("message", "商户未配置 Stripe Connect，不会抽成，所有资金直接进入平台账户");
                result.put("feePercentage", null);
                result.put("feeFixed", null);
                return ResponseEntity.ok(ApiResponse.success("查询成功", result));
            }
            
            result.put("hasConfig", true);
            result.put("enabled", config.getEnabled() != null && config.getEnabled());
            result.put("stripeAccountId", config.getStripeAccountId());
            result.put("currency", config.getCurrency());
            
            // 手续费配置
            Double feePercentage = config.getApplicationFeePercentage();
            Double feeFixed = config.getApplicationFeeFixed();
            Double amexFeePercentage = config.getAmexApplicationFeePercentage();
            Double amexFeeFixed = config.getAmexApplicationFeeFixed();
            
            // 如果商户未配置，使用默认值
            if (feePercentage == null) {
                feePercentage = 2.5; // 默认 2.5%
            }
            if (feeFixed == null) {
                feeFixed = 0.30; // 默认 $0.30
            }
            if (amexFeePercentage == null) {
                amexFeePercentage = 3.5; // 默认 3.5%
            }
            if (amexFeeFixed == null) {
                amexFeeFixed = 0.30; // 默认 $0.30
            }
            
            result.put("feePercentage", feePercentage);
            result.put("feeFixed", feeFixed);
            result.put("amexFeePercentage", amexFeePercentage);
            result.put("amexFeeFixed", amexFeeFixed);
            
            // 抽成状态说明
            if (config.getEnabled() != null && config.getEnabled() 
                    && config.getStripeAccountId() != null && !config.getStripeAccountId().isEmpty()) {
                result.put("message", "✅ 已启用抽成：平台收取 " + feePercentage + "% + $" + feeFixed + "，剩余金额转给商户");
                result.put("merchantDashboardUrl", "https://dashboard.stripe.com/test/connect/accounts/" + config.getStripeAccountId());
            } else {
                result.put("message", "❌ 未启用抽成：所有资金直接进入平台账户");
                if (config.getStripeAccountId() == null || config.getStripeAccountId().isEmpty()) {
                    result.put("reason", "未创建 Connected Account");
                } else {
                    result.put("reason", "Stripe Connect 未启用（enabled=false）");
                }
            }
            
            return ResponseEntity.ok(ApiResponse.success("查询成功", result));
            
        } catch (Exception e) {
            log.error("查询商户抽成状态失败，商户ID: {}", merchantId, e);
            return ResponseEntity.ok(ApiResponse.error(500, "查询失败: " + e.getMessage()));
        }
    }
    
    /**
     * 查询商户 Connected Account 的余额信息
     */
    @GetMapping("/balance/{merchantId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMerchantBalance(@PathVariable String merchantId) {
        log.info("查询商户 Connected Account 余额，商户ID: {}", merchantId);
        
        try {
            MerchantStripeConfig config = merchantStripeConfigMapper.selectByMerchantId(merchantId);
            if (config == null || config.getStripeAccountId() == null || config.getStripeAccountId().isEmpty()) {
                return ResponseEntity.ok(ApiResponse.error(404, "商户未配置 Stripe Connected Account"));
            }
            
            // 查询 Connected Account 信息
            Account account = stripeService.retrieveAccount(config.getStripeAccountId());
            
            Map<String, Object> result = new HashMap<>();
            result.put("accountId", account.getId());
            result.put("accountType", account.getType());
            result.put("chargesEnabled", account.getChargesEnabled());
            result.put("payoutsEnabled", account.getPayoutsEnabled());
            result.put("country", account.getCountry());
            result.put("email", account.getEmail());
            result.put("dashboardUrl", "https://dashboard.stripe.com/test/connect/accounts/" + account.getId());
            result.put("message", "请在 Stripe Dashboard 中查看余额：点击上面的 dashboardUrl 链接");
            
            return ResponseEntity.ok(ApiResponse.success("查询成功", result));
            
        } catch (Exception e) {
            log.error("查询商户余额失败，商户ID: {}", merchantId, e);
            return ResponseEntity.ok(ApiResponse.error(500, "查询失败: " + e.getMessage()));
        }
    }
    
    /**
     * 查询平台账户余额（Application Fee）
     */
    @GetMapping("/platform/balance")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPlatformBalance() {
        log.info("查询平台账户余额");
        
        try {
            // 查询平台账户余额
            com.stripe.model.Balance balance = com.stripe.model.Balance.retrieve();
            
            Map<String, Object> result = new HashMap<>();
            result.put("available", balance.getAvailable());
            result.put("pending", balance.getPending());
            result.put("connectReserved", balance.getConnectReserved());
            result.put("instantAvailable", balance.getInstantAvailable());
            result.put("dashboardUrl", "https://dashboard.stripe.com/test/balance");
            result.put("message", "平台账户余额（Application Fee），可在 Dashboard 中查看详情");
            
            return ResponseEntity.ok(ApiResponse.success("查询成功", result));
            
        } catch (Exception e) {
            log.error("查询平台余额失败", e);
            return ResponseEntity.ok(ApiResponse.error(500, "查询失败: " + e.getMessage()));
        }
    }
}

