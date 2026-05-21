package com.aihub.gateway.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DisplayName("ContentFilter - 网关敏感词过滤器")
class ContentFilterTest {

    private final ContentFilter filter = new ContentFilter();

    private MockServerWebExchange createExchange(String path, String query) {
        String uri = path;
        if (query != null) {
            uri = path + "?" + query;
        }
        MockServerHttpRequest request = MockServerHttpRequest.get(uri).build();
        return MockServerWebExchange.from(request);
    }

    @Nested
    @DisplayName("filter - 敏感词检测")
    class FilterTest {

        @Test
        @DisplayName("路径包含敏感词应返回 400")
        void shouldBlockSensitivePath() {
            MockServerWebExchange exchange = createExchange("/api/test/赌博/hello", null);

            Mono<Void> result = filter.filter(exchange, e -> Mono.empty());

            StepVerifier.create(result).verifyComplete();
            assert exchange.getResponse().getStatusCode() == HttpStatus.BAD_REQUEST;
        }

        @Test
        @DisplayName("查询参数包含敏感词应返回 400")
        void shouldBlockSensitiveQuery() {
            MockServerWebExchange exchange = createExchange("/api/auth/login", "msg=赌博违法");

            Mono<Void> result = filter.filter(exchange, e -> Mono.empty());

            StepVerifier.create(result).verifyComplete();
            assert exchange.getResponse().getStatusCode() == HttpStatus.BAD_REQUEST;
        }

        @Test
        @DisplayName("正常请求应通过")
        void shouldPassCleanRequest() {
            MockServerWebExchange exchange = createExchange("/api/auth/login", "msg=hello");

            Mono<Void> result = filter.filter(exchange, e -> Mono.empty());
            StepVerifier.create(result).verifyComplete();
        }

        @Test
        @DisplayName("URL 编码的敏感词路径应被检测到")
        void shouldDetectEncodedPath() {
            MockServerWebExchange exchange = createExchange("/api/auth/%E8%B5%8C%E5%8D%9A", null);

            Mono<Void> result = filter.filter(exchange, e -> Mono.empty());

            StepVerifier.create(result).verifyComplete();
            assert exchange.getResponse().getStatusCode() == HttpStatus.BAD_REQUEST;
        }

        @Test
        @DisplayName("无查询参数的正常请求通过")
        void shouldPassWithoutQuery() {
            MockServerWebExchange exchange = createExchange("/api/user/profile", null);

            Mono<Void> result = filter.filter(exchange, e -> Mono.empty());
            StepVerifier.create(result).verifyComplete();
        }
    }

    @Nested
    @DisplayName("getOrder - 过滤器优先级")
    class Order {

        @Test
        @DisplayName("返回 -150 确保最先执行")
        void shouldReturnMinus150() {
            assert filter.getOrder() == -150;
        }
    }
}
