package com.aihub.common.util;

import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
public class SensitiveWordFilter {

    private static final Map<Character, Object> ROOT = new HashMap<>();
    private static final Character IS_END = '\0';

    static {
        initDefaultWords();
    }

    public static void addWord(String word) {
        if (word == null || word.isEmpty()) return;
        Map<Character, Object> node = ROOT;
        for (char c : word.toCharArray()) {
            @SuppressWarnings("unchecked")
            Map<Character, Object> next = (Map<Character, Object>) node.computeIfAbsent(c, k -> new HashMap<>());
            node = next;
        }
        node.put(IS_END, Boolean.TRUE);
    }

    public static List<String> check(String text) {
        if (text == null || text.isEmpty()) return Collections.emptyList();
        List<String> found = new ArrayList<>();
        int len = text.length();
        for (int i = 0; i < len; i++) {
            int matchLen = checkAt(text, i);
            if (matchLen > 0) {
                found.add(text.substring(i, i + matchLen));
                i += matchLen - 1;
            }
        }
        return found;
    }

    public static boolean contains(String text) {
        return !check(text).isEmpty();
    }

    public static String replace(String text) {
        if (text == null || text.isEmpty()) return text;
        StringBuilder sb = new StringBuilder(text);
        int len = text.length();
        int offset = 0;
        for (int i = 0; i < len; i++) {
            int matchLen = checkAt(text, i);
            if (matchLen > 0) {
                for (int j = 0; j < matchLen; j++) {
                    sb.setCharAt(i + j - offset, '*');
                }
                i += matchLen - 1;
            }
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static int checkAt(String text, int start) {
        Map<Character, Object> node = ROOT;
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

    private static void initDefaultWords() {
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
            addWord(word);
        }
        log.info("敏感词库初始化完成，共 {} 条", words.length);
    }
}
