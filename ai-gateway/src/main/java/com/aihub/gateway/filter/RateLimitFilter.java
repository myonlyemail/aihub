package com.aihub.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    private static final Map<String, int[]> ROUTE_LIMITS = Map.of(
            "/api/auth/login", new int[]{10, 60},
            "/api/auth/send-code", new int[]{3, 60},
            "/api/auth/sms-login", new int[]{10, 60},
            "/api/chat", new int[]{30, 60},
            "/api/image/generate", new int[]{10, 60},
            "/api/video/generate", new int[]{5, 60}
    );

    private static final int DEFAULT_LIMIT = 60;
    private static final int DEFAULT_WINDOW = 60;

    private static final String LUA_SCRIPT = """
            local key = KEYS[1]
            local limit = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local current = redis.call('INCR', key)
            if current == 1 then
                redis.call('EXPIRE', key, window)
            end
            if current > limit then
                return 0
            end
            return 1
            """;

    public RateLimitFilter(ReactiveRedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        String clientIp = exchange.getRequest().getRemoteAddress() != null
                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                : "unknown";

        final int[] limits = resolveLimit(path);

        String rateKey = "aihub:ratelimit:" + clientIp + ":" + path;

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(LUA_SCRIPT);
        script.setResultType(Long.class);

        final int limit = limits[0];
        final int window = limits[1];

        return redisTemplate.execute(script,
                        List.of(rateKey),
                        List.of(String.valueOf(limit), String.valueOf(window)))
                .next()
                .defaultIfEmpty(1L)
                .flatMap(result -> {
                    if (result == 1) {
                        return chain.filter(exchange);
                    }
                    log.warn("请求被限流: ip={}, path={}, limit={}/{}s", clientIp, path, limit, window);
                    return tooManyRequests(exchange);
                })
                .onErrorResume(e -> {
                    log.warn("RateLimiter Redis 异常，放行请求: {}", e.getMessage());
                    return chain.filter(exchange);
                });
    }

    private int[] resolveLimit(String path) {
        for (Map.Entry<String, int[]> entry : ROUTE_LIMITS.entrySet()) {
            if (path.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return new int[]{DEFAULT_LIMIT, DEFAULT_WINDOW};
    }

    private Mono<Void> tooManyRequests(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\":429,\"msg\":\"请求过于频繁，请稍后重试\"}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -50;
    }
}
