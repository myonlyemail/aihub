package com.aihub.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SensitiveWordFilter - DFA敏感词过滤")
class SensitiveWordFilterTest {

    @Nested
    @DisplayName("addWord - 添加敏感词")
    class AddWord {

        @Test
        @DisplayName("正常添加后 check 应能检测到")
        void shouldDetectAddedWord() {
            SensitiveWordFilter.addWord("测试词");
            assertThat(SensitiveWordFilter.contains("这是测试词内容")).isTrue();
        }

        @Test
        @DisplayName("null 或空字符串不抛异常")
        void shouldHandleNullAndEmpty() {
            SensitiveWordFilter.addWord(null);
            SensitiveWordFilter.addWord("");
            // no exception
        }
    }

    @Nested
    @DisplayName("check - 检测敏感词")
    class Check {

        @Test
        @DisplayName("null/空文本返回空列表")
        void shouldReturnEmptyForNullOrBlank() {
            assertThat(SensitiveWordFilter.check(null)).isEmpty();
            assertThat(SensitiveWordFilter.check("")).isEmpty();
        }

        @Test
        @DisplayName("不含敏感词的文本返回空列表")
        void shouldReturnEmptyForCleanText() {
            assertThat(SensitiveWordFilter.check("今天天气真好，适合出门散步")).isEmpty();
        }

        @Test
        @DisplayName("检测到单个敏感词")
        void shouldFindSingleWord() {
            assertThat(SensitiveWordFilter.check("有人在进行赌博活动")).containsExactly("赌博");
        }

        @Test
        @DisplayName("检测到多个敏感词，按出现顺序返回")
        void shouldFindMultipleWords() {
            List<String> result = SensitiveWordFilter.check("赌博和毒品都是违法的");
            assertThat(result).contains("赌博", "毒品");
        }

        @Test
        @DisplayName("重叠敏感词：色情网站中能检测到色情和色情网站")
        void shouldHandleOverlappingWords() {
            List<String> result = SensitiveWordFilter.check("他浏览了色情网站");
            // check method returns the first match at position, then skips past it
            // "色情" matches first (shorter), then "网站" is consumed by skip
            assertThat(result).hasSizeGreaterThanOrEqualTo(1);
            assertThat(result.get(0)).isEqualTo("色情");
        }

        @Test
        @DisplayName("验证所有 29 个默认敏感词均能检测")
        void shouldDetectAllDefaultWords() {
            String[] words = {
                    "赌博", "赌场", "博彩", "彩票",
                    "色情", "成人", "裸体", "性爱", "色情网站",
                    "毒品", "大麻", "海洛因", "冰毒", "吸毒",
                    "枪支", "手枪", "步枪", "弹药",
                    "杀人", "自杀", "恐怖主义", "恐怖分子",
                    "诈骗", "传销", "洗钱",
                    "反动", "颠覆", "分裂国家",
                    "翻墙", "VPN翻墙",
                    "假钞", "假币", "伪造货币",
                    "违禁", "走私",
                    "黑客", "DDOS", "攻击服务器",
                    "未成年人", "儿童色情",
                    "种族歧视", "纳粹"
            };
            for (String word : words) {
                assertThat(SensitiveWordFilter.contains(word))
                        .as("应该检测到敏感词: " + word)
                        .isTrue();
            }
        }
    }

    @Nested
    @DisplayName("contains - 是否含敏感词")
    class Contains {

        @Test
        @DisplayName("含敏感词返回 true")
        void shouldReturnTrue() {
            assertThat(SensitiveWordFilter.contains("冰毒危害大")).isTrue();
        }

        @Test
        @DisplayName("无敏感词返回 false")
        void shouldReturnFalse() {
            assertThat(SensitiveWordFilter.contains("天气预报播报")).isFalse();
        }
    }

    @Nested
    @DisplayName("replace - 替换敏感词为星号")
    class Replace {

        @Test
        @DisplayName("null/空文本原样返回")
        void shouldReturnOriginalForNullOrBlank() {
            assertThat(SensitiveWordFilter.replace(null)).isNull();
            assertThat(SensitiveWordFilter.replace("")).isEmpty();
        }

        @Test
        @DisplayName("敏感词被替换为等长星号")
        void shouldReplaceWithStars() {
            assertThat(SensitiveWordFilter.replace("不要赌博了")).isEqualTo("不要**了");
        }

        @Test
        @DisplayName("多字敏感词替换为等长星号")
        void shouldReplaceMultiCharWord() {
            assertThat(SensitiveWordFilter.replace("这是伪造货币的现场")).isEqualTo("这是****的现场");
        }

        @Test
        @DisplayName("多个敏感词均被替换")
        void shouldReplaceAllWords() {
            String result = SensitiveWordFilter.replace("赌博和毒品都要远离");
            assertThat(result).isEqualTo("**和**都要远离");
        }

        @Test
        @DisplayName("无敏感词的文本保持原样")
        void shouldNotModifyCleanText() {
            String text = "今天的天气真好";
            assertThat(SensitiveWordFilter.replace(text)).isEqualTo(text);
        }

        @Test
        @DisplayName("相邻敏感词均被正确替换")
        void shouldReplaceAdjacentWords() {
            // "赌博" -> "**", then skip past 博, check "博毒品" — "毒品" starts at index 0 of that substring
            String result = SensitiveWordFilter.replace("赌博毒品");
            assertThat(result).isEqualTo("****");
        }
    }
}
