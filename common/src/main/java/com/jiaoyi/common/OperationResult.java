package com.jiaoyi.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 操作结果（用于幂等性接口返回）
 * 区分：成功、幂等成功（已处理过）、失败
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OperationResult {
    
    /**
     * 结果状态
     */
    private ResultStatus status;
    
    /**
     * 消息
     */
    private String message;
    
    /**
     * 结果状态枚举
     */
    public enum ResultStatus {
        /**
         * 成功：第一次调用，操作成功
         */
        SUCCESS(200, "操作成功"),
        
        /**
         * 幂等成功：重复调用，但操作已经成功过（幂等）
         */
        IDEMPOTENT_SUCCESS(200, "操作已成功（幂等：重复调用）"),
        
        /**
         * 失败：操作失败
         */
        FAILED(400, "操作失败");
        
        private final int code;
        private final String description;
        
        ResultStatus(int code, String description) {
            this.code = code;
            this.description = description;
        }
        
        public int getCode() {
            return code;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * 创建成功结果
     */
    public static OperationResult success(String message) {
        return new OperationResult(ResultStatus.SUCCESS, message);
    }
    
    /**
     * 创建幂等成功结果
     */
    public static OperationResult idempotentSuccess(String message) {
        return new OperationResult(ResultStatus.IDEMPOTENT_SUCCESS, message);
    }
    
    /**
     * 创建失败结果
     */
    public static OperationResult failed(String message) {
        return new OperationResult(ResultStatus.FAILED, message);
    }
}


