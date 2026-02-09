package com.jiaoyi.common;

import com.jiaoyi.common.constants.ResponseCode;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 统一API响应格式
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    private Integer code;
    private String message;
    private T data;

    /**
     * 成功响应
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(ResponseCode.SUCCESS.getCode(), ResponseCode.SUCCESS.getMessage(), data);
    }

    /**
     * 成功响应（无数据）
     */
    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(ResponseCode.SUCCESS.getCode(), ResponseCode.SUCCESS.getMessage(), null);
    }

    /**
     * 成功响应（自定义消息）
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(ResponseCode.SUCCESS.getCode(), message, data);
    }

    /**
     * 失败响应（使用ResponseCode枚举）
     */
    public static <T> ApiResponse<T> error(ResponseCode responseCode) {
        return new ApiResponse<>(responseCode.getCode(), responseCode.getMessage(), null);
    }

    /**
     * 失败响应（使用ResponseCode枚举 + 自定义消息）
     */
    public static <T> ApiResponse<T> error(ResponseCode responseCode, String customMessage) {
        return new ApiResponse<>(responseCode.getCode(), customMessage, null);
    }

    /**
     * 失败响应（默认系统错误）
     * @deprecated 建议使用 error(ResponseCode) 方法
     */
    @Deprecated
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(ResponseCode.INTERNAL_ERROR.getCode(), message, null);
    }

    /**
     * 失败响应（自定义状态码）
     * @deprecated 建议使用 error(ResponseCode) 方法
     */
    @Deprecated
    public static <T> ApiResponse<T> error(Integer code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}


