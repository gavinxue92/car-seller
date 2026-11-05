package com.carseller.gateway.support;

import com.carseller.common.result.R;
import com.carseller.common.result.ResultCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 网关侧的 JSON 响应写出器。
 *
 * <p>用途：WebFlux 环境下网关不经过业务服务的 {@code GlobalExceptionHandler}，
 * 因此 401 等拦截结果需要由网关自行写出——统一为 {@code R} 结构，
 * 并保留真实的 HTTP 状态码（网关 401/429/503 属例外，不使用 HTTP 200）。</p>
 *
 * @author car-seller
 */
@Component
public class GatewayResponseWriter {

    private static final Logger log = LoggerFactory.getLogger(GatewayResponseWriter.class);

    private final ObjectMapper objectMapper;

    public GatewayResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 写出 401 未认证响应体。
     *
     * @param exchange 当前请求上下文
     * @param message  面向用户的提示（中文）
     */
    public Mono<Void> writeUnauthorized(ServerWebExchange exchange, String message) {
        return write(exchange, HttpStatus.UNAUTHORIZED, R.fail(ResultCode.UNAUTHORIZED, message));
    }

    /**
     * 写出指定状态码 + {@code R} 响应体。
     */
    public Mono<Void> write(ServerWebExchange exchange, HttpStatus status, R<?> body) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            log.warn("响应已提交，忽略本次写出: status={}", status.value());
            return Mono.empty();
        }
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = response.bufferFactory().wrap(serialize(body));
        return response.writeWith(Mono.just(buffer));
    }

    private byte[] serialize(R<?> body) {
        try {
            return objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            // R 为简单 POJO，序列化失败属于不可恢复的内部错误，必须抛出而不是静默返回空响应
            throw new IllegalStateException("网关响应体序列化失败", e);
        }
    }
}
