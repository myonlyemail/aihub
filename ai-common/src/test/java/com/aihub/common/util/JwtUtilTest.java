package com.aihub.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JwtUtil - JWT令牌工具")
class JwtUtilTest {

    @Nested
    @DisplayName("createToken - 生成令牌")
    class CreateToken {

        @Test
        @DisplayName("仅带 userId 的令牌")
        void shouldCreateTokenWithUserId() {
            String token = JwtUtil.createToken(1L);
            assertThat(token).isNotNull().isNotEmpty();
        }

        @Test
        @DisplayName("带 userId 和 tenantId 的令牌")
        void shouldCreateTokenWithTenantId() {
            String token = JwtUtil.createToken(1L, 100L);
            assertThat(token).isNotNull().isNotEmpty();
        }

        @Test
        @DisplayName("不同 userId 生成不同令牌")
        void shouldGenerateDifferentTokensForDifferentUsers() {
            String token1 = JwtUtil.createToken(1L);
            String token2 = JwtUtil.createToken(2L);
            assertThat(token1).isNotEqualTo(token2);
        }
    }

    @Nested
    @DisplayName("verify - 验证令牌")
    class Verify {

        @Test
        @DisplayName("合法令牌验证通过")
        void shouldVerifyValidToken() {
            String token = JwtUtil.createToken(1L);
            assertThat(JwtUtil.verify(token)).isTrue();
        }

        @Test
        @DisplayName("空字符串验证失败")
        void shouldFailForEmptyToken() {
            assertThat(JwtUtil.verify("")).isFalse();
        }

        @Test
        @DisplayName("null 验证失败")
        void shouldFailForNullToken() {
            assertThat(JwtUtil.verify(null)).isFalse();
        }

        @Test
        @DisplayName("伪造令牌验证失败")
        void shouldFailForFakeToken() {
            assertThat(JwtUtil.verify("not.a.valid.token")).isFalse();
        }

        @Test
        @DisplayName("随机字符串验证失败")
        void shouldFailForRandomString() {
            assertThat(JwtUtil.verify("randomString12345")).isFalse();
        }
    }

    @Nested
    @DisplayName("getUserId - 解析 userId")
    class GetUserId {

        @Test
        @DisplayName("从有效令牌中提取 userId")
        void shouldExtractUserId() {
            String token = JwtUtil.createToken(99L);
            assertThat(JwtUtil.getUserId(token)).isEqualTo(99L);
        }

        @Test
        @DisplayName("非法令牌返回 null")
        void shouldReturnNullForInvalidToken() {
            assertThat(JwtUtil.getUserId("invalid")).isNull();
        }

        @Test
        @DisplayName("null 令牌返回 null")
        void shouldReturnNullForNullToken() {
            assertThat(JwtUtil.getUserId(null)).isNull();
        }
    }

    @Nested
    @DisplayName("getTenantId - 解析租户ID")
    class GetTenantId {

        @Test
        @DisplayName("从带 tenantId 的令牌中提取")
        void shouldExtractTenantId() {
            String token = JwtUtil.createToken(1L, 500L);
            assertThat(JwtUtil.getTenantId(token)).isEqualTo(500L);
        }

        @Test
        @DisplayName("不带 tenantId 的令牌返回 null")
        void shouldReturnNullWhenNoTenantId() {
            String token = JwtUtil.createToken(1L);
            assertThat(JwtUtil.getTenantId(token)).isNull();
        }
    }

    @Nested
    @DisplayName("完整往返测试")
    class RoundTrip {

        @Test
        @DisplayName("createToken → verify → getUserId 完整流程")
        void shouldRoundTrip() {
            String token = JwtUtil.createToken(42L, 7L);
            assertThat(JwtUtil.verify(token)).isTrue();
            assertThat(JwtUtil.getUserId(token)).isEqualTo(42L);
            assertThat(JwtUtil.getTenantId(token)).isEqualTo(7L);
        }
    }
}
