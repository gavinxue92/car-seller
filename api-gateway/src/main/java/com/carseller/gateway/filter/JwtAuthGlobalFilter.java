package com.carseller.gateway.filter;

import com.carseller.common.security.JwtPayload;
import com.carseller.common.security.JwtProperties;
import com.carseller.common.security.JwtUtils;
import com.carseller.common.security.UserHeaders;
import com.carseller.gateway.config.GatewayAuthProperties;
import com.carseller.gateway.support.GatewayResponseWriter;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 全局 JWT 鉴权过滤器。
 *
 * <p>处理流程（见 README 10.2）：</p>
 * <ol>
 *     <li>白名单路径（商品 GET、登录、探活、CORS 预检）直接放行；</li>
 *     <li>其余请求解析 {@code Authorization: Bearer <JWT>}：缺失 / 格式错误 / 签名错误 / 已过期 → HTTP 401 + {@code R} 响应体；</li>
 *     <li><b>安全红线</b>：无论是否白名单，先删除客户端自带的 {@code X-User-Id}、{@code X-User-Name}、
 *         {@code X-Request-Id}（视为不可信），再由网关重新注入；</li>
 *     <li>校验通过后注入受信任头：{@code X-User-Id}=JWT sub、{@code X-User-Name}=JWT userName（URL 编码）、
 *         {@code X-Request-Id}=网关新生成的 UUID，向下游透传。</li>
 * </ol>
 *
 * <p>下游服务只信任网关注入的 {@code X-User-Id}，禁止默认值兜底。</p>
 *
 * @author car-seller
 */
@Component
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthGlobalFilter.class);

    /** 过滤器顺序：早于所有路由型过滤器执行，鉴权不过的请求不再进入负载均衡 */
    private static final int FILTER_ORDER = Ordered.HIGHEST_PRECEDENCE + 10;

    /** 白名单中的方法通配符 */
    private static final String METHOD_WILDCARD = "*";

    /** 白名单条目 "METHOD path" 的分隔符 */
    private static final String RULE_SEPARATOR_REGEX = "\\s+";

    private static final PathMatcher PATH_MATCHER = new AntPathMatcher();

    private final JwtProperties jwtProperties;

    private final GatewayResponseWriter responseWriter;

    private final List<WhitelistRule> whitelistRules;

    private final SecretKey signingKey;

    public JwtAuthGlobalFilter(JwtProperties jwtProperties,
                               GatewayAuthProperties authProperties,
                               GatewayResponseWriter responseWriter) {
        this.jwtProperties = jwtProperties;
        this.responseWriter = responseWriter;
        this.whitelistRules = parseWhitelist(authProperties.getWhitelist());
        // 密钥非法时直接启动失败，避免"运行期才发现弱密钥"导致线上鉴权不可用
        this.signingKey = JwtUtils.hmacKey(jwtProperties.getSecret());
        if (JwtProperties.DEFAULT_SECRET.equals(jwtProperties.getSecret())) {
            log.warn("网关正在使用默认 jwt.secret（仅限本地开发），生产环境请通过环境变量 JWT_SECRET 覆盖");
        }
        log.info("网关 JWT 鉴权已启用：白名单 {} 条，Token 有效期 {} 小时",
                whitelistRules.size(), jwtProperties.getExpireHours());
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getRawPath();

        // 1. 白名单：不做身份校验，但仍需清理伪造头，避免下游误信
        if (isWhitelisted(request.getMethod(), path)) {
            return chain.filter(withTrustedHeaders(exchange, request, null));
        }

        // 2. 提取 Token：缺失或格式非法直接 401
        String token = resolveToken(request);
        if (token == null) {
            log.warn("鉴权失败：缺少合法的 {} 请求头，path={}", UserHeaders.AUTHORIZATION, path);
            return responseWriter.writeUnauthorized(exchange, "未登录或访问令牌缺失");
        }

        // 3. 校验签名与有效期：失败统一 401（不把异常细节抛给客户端）
        JwtPayload payload;
        try {
            payload = JwtUtils.parseToken(token, signingKey);
        } catch (ExpiredJwtException e) {
            log.warn("鉴权失败：访问令牌已过期，path={}", path);
            return responseWriter.writeUnauthorized(exchange, "登录已过期，请重新登录");
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("鉴权失败：访问令牌无效，path={}, reason={}", path, e.getMessage());
            return responseWriter.writeUnauthorized(exchange, "访问令牌无效");
        }

        // 4. 注入受信任的用户上下文，向下游透传
        return chain.filter(withTrustedHeaders(exchange, request, payload));
    }

    @Override
    public int getOrder() {
        return FILTER_ORDER;
    }

    /**
     * 组装携带受信任请求头的 exchange：先摘除客户端伪造头，再按需注入身份头。
     *
     * @param payload 已校验的用户上下文；null 表示白名单请求，不注入身份信息
     */
    private ServerWebExchange withTrustedHeaders(ServerWebExchange exchange,
                                                 ServerHttpRequest request,
                                                 JwtPayload payload) {
        ServerHttpRequest trustedRequest = request.mutate()
                .headers(headers -> {
                    // 安全红线：客户端自带的身份头一律不可信，先删除再注入
                    headers.remove(UserHeaders.X_USER_ID);
                    headers.remove(UserHeaders.X_USER_NAME);
                    headers.remove(UserHeaders.X_REQUEST_ID);
                    if (payload != null) {
                        headers.set(UserHeaders.X_USER_ID, String.valueOf(payload.getUserId()));
                        if (StringUtils.hasText(payload.getUserName())) {
                            // 请求头只允许 ASCII，中文用户名按 UTF-8 百分号编码透传，下游用 URLDecoder 还原
                            headers.set(UserHeaders.X_USER_NAME, encodeHeaderValue(payload.getUserName()));
                        }
                    }
                    headers.set(UserHeaders.X_REQUEST_ID, UUID.randomUUID().toString());
                })
                .build();
        return exchange.mutate().request(trustedRequest).build();
    }

    /**
     * 判断当前请求是否命中白名单：CORS 预检请求（OPTIONS）由跨域过滤器处理，直接放行。
     */
    private boolean isWhitelisted(HttpMethod method, String path) {
        if (HttpMethod.OPTIONS.equals(method)) {
            return true;
        }
        String methodName = method == null ? METHOD_WILDCARD : method.name();
        for (WhitelistRule rule : whitelistRules) {
            if (rule.matches(methodName, path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从 {@code Authorization} 头中提取 Token；缺失或前缀不匹配返回 null。
     */
    private String resolveToken(ServerHttpRequest request) {
        String headerName = StringUtils.hasText(jwtProperties.getHeader())
                ? jwtProperties.getHeader()
                : UserHeaders.AUTHORIZATION;
        String headerValue = request.getHeaders().getFirst(headerName);
        if (!StringUtils.hasText(headerValue)) {
            return null;
        }
        String prefix = jwtProperties.getPrefix();
        if (!StringUtils.hasText(prefix)) {
            return headerValue.trim();
        }
        if (headerValue.length() <= prefix.length()
                || !headerValue.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return null;
        }
        String token = headerValue.substring(prefix.length()).trim();
        return StringUtils.hasText(token) ? token : null;
    }

    private String encodeHeaderValue(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * 解析 yml 中的白名单条目：{@code "METHOD path"} 或 {@code "path"}（等价于任意方法）。
     */
    private static List<WhitelistRule> parseWhitelist(List<String> entries) {
        List<WhitelistRule> rules = new ArrayList<>();
        if (entries == null || entries.isEmpty()) {
            return rules;
        }
        for (String entry : entries) {
            if (!StringUtils.hasText(entry)) {
                continue;
            }
            String[] parts = entry.trim().split(RULE_SEPARATOR_REGEX, 2);
            String method = parts.length == 2 ? parts[0].toUpperCase(Locale.ROOT) : METHOD_WILDCARD;
            String pattern = parts.length == 2 ? parts[1].trim() : parts[0].trim();
            if (!StringUtils.hasText(pattern)) {
                throw new IllegalStateException("gateway.auth.whitelist 配置非法（应为 \"METHOD path\"）：" + entry);
            }
            rules.add(new WhitelistRule(method, pattern));
        }
        return rules;
    }

    /**
     * 单条白名单规则：HTTP 方法（支持通配）+ Ant 风格路径模式。
     */
    private static final class WhitelistRule {

        private final String method;

        private final String pathPattern;

        private WhitelistRule(String method, String pathPattern) {
            this.method = method;
            this.pathPattern = pathPattern;
        }

        private boolean matches(String requestMethod, String requestPath) {
            boolean methodMatched = METHOD_WILDCARD.equals(method) || method.equalsIgnoreCase(requestMethod);
            return methodMatched && PATH_MATCHER.match(pathPattern, requestPath);
        }
    }
}
