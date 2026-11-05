package com.carseller.common.exception;

import com.carseller.common.result.ResultCode;

import java.io.Serial;

/**
 * 业务异常：用于表示可预期的业务错误（如参数非法、资源不存在等），
 * 由 {@link GlobalExceptionHandler} 统一转换为 {@code R} 响应。
 *
 * <p>所在模块：{@code car-seller-common}，各业务微服务禁止复制本类。</p>
 *
 * @author car-seller
 */
public class BusinessException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 业务状态码 */
    private final Integer code;

    public BusinessException(String message) {
        super(message);
        this.code = ResultCode.FAILURE.getCode();
    }

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }

    public Integer getCode() {
        return code;
    }
}
