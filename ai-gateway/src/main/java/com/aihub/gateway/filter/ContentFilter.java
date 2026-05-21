package com.aihub.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@Component
public class ContentFilter implements GlobalFilter, Ordered {

    private static final Map<Character, Object> DFA_ROOT = new HashMap<>();
    private static final Character IS_END = '\0';

    static {
        String[] words = {
                "赌博", "赌场", "博彩", "彩票",
                "色情", "成人电影", "裸体", "性爱", "色情网站",
                "毒品", "大麻", "海洛因", "冰毒", "吸毒",
                "枪支", "手枪", "步枪", "弹药",
                "杀人", "自杀", "恐怖主义", "恐怖分子",
                "诈骗", "传销", "洗钱",
                "反动", "颠覆", "分裂国家",
                "假钞", "假币", "伪造货币",
                "违禁", "走私",
                "黑客攻击", "DDOS",
                "儿童色情",
                "种族歧视", "纳粹"
        };
        for (String word : words) {
            addDfaWord(word);
        }
    }

    private static void addDfaWord(String word) {
        Map<Character, Object> node = DFA_ROOT;
        for (char c : word.toCharArray()) {
            @SuppressWarnings("unchecked")
            Map<Character, Object> next = (Map<Character, Object>) node.computeIfAbsent(c, k -> new HashMap<>());
            node = next;
        }
        node.put(IS_END, Boolean.TRUE);
    }

    private static List<String> checkText(String text) {
        if (text == null || text.isEmpty()) return Collections.emptyList();
        List<String> found = new ArrayList<>();
        int len = text.length();
        for (int i = 0; i < len; i++) {
            int matchLen = matchAt(text, i);
            if (matchLen > 0) {
                found.add(text.substring(i, i + matchLen));
                i += matchLen - 1;
            }
        }
        return found;
    }

    @SuppressWarnings("unchecked")
    private static int matchAt(String text, int start) {
        Map<Character, Object> node = DFA_ROOT;
        int matchLen = 0;
        for (int i = start; i < text.length(); i++) {
            Object next = node.get(text.charAt(i));
            if (next == null) break;
            node = (Map<Character, Object>) next;
            matchLen++;
            if (Boolean.TRUE.equals(node.get(IS_END))) {
                return matchLen;
            }
        }
        return 0;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // Check URL path (decode percent-encoded characters first)
        String decodedPath = URLDecoder.decode(path, StandardCharsets.UTF_8);
        List<String> pathWords = checkText(decodedPath);
        if (!pathWords.isEmpty()) {
            log.warn("URL路径包含敏感词: path={}, words={}", path, pathWords);
            return badRequest(exchange, "请求包含敏感内容");
        }

        // Check query params
        String query = exchange.getRequest().getURI().getQuery();
        if (query != null) {
            List<String> words = checkText(query);
            if (!words.isEmpty()) {
                log.warn("URL参数包含敏感词: path={}, words={}", path, words);
                return badRequest(exchange, "请求包含敏感内容");
            }
        }

        return chain.filter(exchange);
    }

    private Mono<Void> badRequest(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.BAD_REQUEST);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"code\":400,\"msg\":\"" + message + "\"}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -150;
    }
}
