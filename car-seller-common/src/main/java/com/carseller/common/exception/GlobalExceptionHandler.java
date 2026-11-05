package com.carseller.common.exception;

import com.carseller.common.result.R;
import com.carseller.common.result.ResultCode;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

/**
 * 全局异常处理：保证任何异常返回的响应体都是统一的 {@code R} 结构。
 *
 * <p>所在模块：{@code car-seller-common}，各业务微服务禁止复制本类。
 * 业务服务启动类需通过
 * {@code @ComponentScan({"com.carseller.<svc>", "com.carseller.common"})}
 * 保证本组件被扫描注册。</p>
 *
 * @author car-seller
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 业务异常。
     */
    @ExceptionHandler(BusinessException.class)
    public R<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return R.fail(e.getCode(), e.getMessage());
    }

    /**
     * 请求体 / 表单参数校验失败（@Valid + @RequestBody / @ModelAttribute）。
     * 说明：MethodArgumentNotValidException 是 BindException 的子类，这里统一处理。
     */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public R<Void> handleBindException(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .filter(text -> text != null && !text.isBlank())
                .collect(Collectors.joining("; "));
        if (message.isBlank()) {
            message = ResultCode.BAD_REQUEST.getMessage();
        }
        log.warn("参数校验失败: {}", message);
        return R.fail(ResultCode.BAD_REQUEST.getCode(), message);
    }

    /**
     * 方法参数校验失败（@Validated + @RequestParam / @PathVariable）。
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public R<Void> handleConstraintViolationException(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(violation -> violation.getMessage())
                .filter(text -> text != null && !text.isBlank())
                .collect(Collectors.joining("; "));
        if (message.isBlank()) {
            message = ResultCode.BAD_REQUEST.getMessage();
        }
        log.warn("参数校验失败: {}", message);
        return R.fail(ResultCode.BAD_REQUEST.getCode(), message);
    }

    /**
     * 请求参数缺失、类型不匹配、请求体解析失败等客户端错误。
     */
    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public R<Void> handleBadRequest(Exception e) {
        log.warn("请求参数错误: {}", e.getMessage());
        return R.fail(ResultCode.BAD_REQUEST.getCode(), "请求参数格式不正确：" + e.getMessage());
    }

    /**
     * 兜底异常处理。
     */
    @ExceptionHandler(Exception.class)
    public R<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return R.fail(ResultCode.FAILURE.getCode(), "系统繁忙，请稍后重试");
    }
}
