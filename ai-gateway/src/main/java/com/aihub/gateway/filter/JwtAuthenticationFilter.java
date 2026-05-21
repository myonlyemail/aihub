package com.aihub.gateway.filter;

import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import cn.hutool.jwt.signers.JWTSignerUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final String SECRET = "aihub-secret-key-2024";

    private static final List<String> WHITE_URLS = List.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/send-code",
            "/api/auth/sms-login",
            "/api/auth/wechat-login",
            "/api/auth/oauth-login",
            "/doc.html",
            "/webjars",
            "/v3/api-docs",
            "/swagger-resources",
            "/favicon.ico",
            "/error"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        if (isWhiteUrl(path)) {
            return chain.filter(exchange);
        }

        String token = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (token == null || token.isBlank()) {
            return unauthorized(exchange, "未提供认证Token");
        }

        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        try {
            boolean verified = JWTUtil.verify(token, JWTSignerUtil.hs256(SECRET.getBytes()));
            if (!verified) {
                return unauthorized(exchange, "Token无效或已过期");
            }

            JWT jwt = JWTUtil.parseToken(token);
            Object userId = jwt.getPayload("userId");
            Object tenantId = jwt.getPayload("tenantId");

            ServerHttpRequest mutatedRequest = request.mutate()
                    .header("X-User-Id", String.valueOf(userId))
                    .header("X-Tenant-Id", tenantId != null ? String.valueOf(tenantId) : "0")
                    .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());

        } catch (Exception e) {
            log.warn("Token 校验异常: {}", e.getMessage());
            return unauthorized(exchange, "Token校验失败");
        }
    }

    private boolean isWhiteUrl(String path) {
        return WHITE_URLS.stream().anyMatch(url ->
                path.startsWith(url) || path.contains(url));
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\":401,\"msg\":\"" + message + "\"}";
        DataBuffer buffer = response.bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
