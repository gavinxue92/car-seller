package com.carseller.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 网关鉴权配置（对应 application.yml 中的 {@code gateway.auth.*}）。
 *
 * <p>白名单条目格式：{@code "HTTP方法 路径模式"}，方法支持 {@code *} 表示任意方法；
 * 也可只写路径模式（等价于 {@code * 路径模式}）。路径模式使用 Ant 风格（{@code /api/products/**}）。</p>
 *
 * <p>白名单只放行"无需登录即可访问"的读接口与登录/探活端点；
 * 其余请求必须携带合法 JWT，否则网关直接返回 401。</p>
 *
 * @author car-seller
 */
@ConfigurationProperties(prefix = "gateway.auth")
public class GatewayAuthProperties {

    /** 免鉴权路径：如 {@code GET /api/products/**}、{@code POST /api/auth/login} */
    private List<String> whitelist = new ArrayList<>();

    public List<String> getWhitelist() {
        return whitelist;
    }

    public void setWhitelist(List<String> whitelist) {
        this.whitelist = whitelist;
    }
}
