package com.example.app.memory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 关键词检索器（Sparse Retrieval）
 *
 * <p>基于 Redis 倒排索引实现关键词精确匹配检索，与向量检索（Dense Retrieval）互补：
 * <ul>
 *   <li>向量检索：擅长语义相似（"用 Java" ≈ "技术栈是 Java"）</li>
 *   <li>关键词检索：擅长精确匹配（"KChat" 就是 KChat，不会漏）</li>
 * </ul>
 *
 * <p>索引结构：
 * <pre>
 * Redis Hash: memory:keyword:{userId}
 *   field="kchat" → value="1,5,12"  (逗号分隔的 memoryId 列表)
 *   field="java"  → value="1,3"
 * </pre>
 *
 * <p>检索时对 query 做简单分词 + 停用词过滤，逐个关键词查 Hash 取交集。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class KeywordRetriever {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String KEYWORD_INDEX_PREFIX = "memory:keyword:";

    /** 停用词（中英文通用） */
    private static final Set<String> STOP_WORDS = Set.of(
            "的", "了", "是", "在", "我", "你", "他", "她", "它",
            "这", "那", "个", "和", "与", "或", "不", "也", "都", "就",
            "有", "没", "被", "让", "把", "给", "对", "向", "从", "到",
            "the", "a", "an", "is", "are", "was", "were", "do", "does",
            "did", "have", "has", "had", "can", "could", "should", "would",
            "will", "shall", "may", "might", "must", "need", "dare", "ought",
            "used", "to", "of", "in", "for", "on", "with", "at", "by", "from"
    );

    /**
     * 从记忆内容中提取关键词
     *
     * @param content 记忆内容
     * @return 关键词列表（已去重、小写、过滤停用词）
     */
    public List<String> extractKeywords(String content) {
        if (content == null || content.isBlank()) {
            return Collections.emptyList();
        }

        // 简单分词：按非字母数字和中文字符分割
        String[] tokens = content.toLowerCase().split("[\\s,，。？?!！；;：:、\\.\\(\\)\\[\\]\\{\\}\\-_/\\\\]+");

        Set<String> keywords = new LinkedHashSet<>();
        for (String token : tokens) {
            String t = token.trim().toLowerCase();
            if (t.length() < 2 || STOP_WORDS.contains(t)) {
                continue;
            }
            keywords.add(t);
        }

        return new ArrayList<>(keywords);
    }

    /**
     * 为记忆构建关键词倒排索引
     *
     * @param userId   用户 ID
     * @param memoryId 记忆 ID
     * @param content  记忆内容
     */
    public void indexKeywords(String userId, Long memoryId, String content) {
        List<String> keywords = extractKeywords(content);
        if (keywords.isEmpty()) {
            return;
        }

        String indexKey = KEYWORD_INDEX_PREFIX + userId;
        String memoryIdStr = memoryId.toString();

        try {
            for (String keyword : keywords) {
                redisTemplate.opsForHash().put(indexKey, keyword, memoryIdStr);
            }
            // 设置索引 TTL（30 天）
            redisTemplate.expire(indexKey, 30, TimeUnit.DAYS);
            log.debug("[KeywordRetriever] Indexed {} keywords for memory {} (user: {})",
                    keywords.size(), memoryId, userId);
        } catch (Exception e) {
            log.warn("[KeywordRetriever] Failed to index keywords for memory {}: {}",
                    memoryId, e.getMessage());
        }
    }

    /**
     * 批量为记忆构建关键词倒排索引
     */
    public void indexKeywordsBatch(String userId, List<KeywordIndexEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }

        String indexKey = KEYWORD_INDEX_PREFIX + userId;

        try {
            for (KeywordIndexEntry entry : entries) {
                List<String> keywords = extractKeywords(entry.content());
                String memoryIdStr = entry.memoryId().toString();
                for (String keyword : keywords) {
                    redisTemplate.opsForHash().put(indexKey, keyword, memoryIdStr);
                }
            }
            redisTemplate.expire(indexKey, 30, TimeUnit.DAYS);
            log.debug("[KeywordRetriever] Batch indexed {} entries for user {}",
                    entries.size(), userId);
        } catch (Exception e) {
            log.warn("[KeywordRetriever] Failed batch keyword indexing: {}", e.getMessage());
        }
    }

    /**
     * 删除记忆的关键词索引
     */
    public void removeKeywords(String userId, Long memoryId) {
        String indexKey = KEYWORD_INDEX_PREFIX + userId;
        String memoryIdStr = memoryId.toString();

        try {
            // 遍历 Hash 的所有 field，删除包含此 memoryId 的
            Map<Object, Object> allEntries = redisTemplate.opsForHash().entries(indexKey);
            if (allEntries != null) {
                for (Map.Entry<Object, Object> entry : allEntries.entrySet()) {
                    String value = entry.getValue() != null ? entry.getValue().toString() : "";
                    if (value.equals(memoryIdStr) || value.contains(memoryIdStr + ",")) {
                        redisTemplate.opsForHash().delete(indexKey, entry.getKey());
                    }
                }
            }
            log.debug("[KeywordRetriever] Removed keywords for memory {} (user: {})",
                    memoryId, userId);
        } catch (Exception e) {
            log.warn("[KeywordRetriever] Failed to remove keywords for memory {}: {}",
                    memoryId, e.getMessage());
        }
    }

    /**
     * 关键词检索：根据 query 中的关键词查找匹配的记忆 ID
     *
     * @param userId     用户 ID
     * @param query      查询文本
     * @param maxResults 最大返回数量
     * @return 匹配的记忆 ID 列表（按关键词匹配数排序）
     */
    public List<KeywordMatch> search(String userId, String query, int maxResults) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        List<String> queryKeywords = extractKeywords(query);
        if (queryKeywords.isEmpty()) {
            return Collections.emptyList();
        }

        String indexKey = KEYWORD_INDEX_PREFIX + userId;

        // 统计每个 memoryId 的关键词匹配数
        Map<Long, Integer> matchCounts = new HashMap<>();
        Map<Long, List<String>> matchedKeywords = new HashMap<>();

        try {
            for (String keyword : queryKeywords) {
                Object value = redisTemplate.opsForHash().get(indexKey, keyword);
                if (value != null) {
                    String[] memoryIds = value.toString().split(",");
                    for (String idStr : memoryIds) {
                        try {
                            Long memoryId = Long.parseLong(idStr.trim());
                            matchCounts.merge(memoryId, 1, Integer::sum);
                            matchedKeywords.computeIfAbsent(memoryId, k -> new ArrayList<>())
                                    .add(keyword);
                        } catch (NumberFormatException e) {
                            // skip
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[KeywordRetriever] Failed to search keywords: {}", e.getMessage());
            return Collections.emptyList();
        }

        if (matchCounts.isEmpty()) {
            return Collections.emptyList();
        }

        // 按匹配数降序排序，取 top N
        double totalKeywords = queryKeywords.size();
        return matchCounts.entrySet().stream()
                .map(entry -> {
                    double score = entry.getValue() / totalKeywords; // 归一化到 0~1
                    return new KeywordMatch(
                            entry.getKey(),
                            score,
                            entry.getValue(),
                            matchedKeywords.getOrDefault(entry.getKey(), Collections.emptyList())
                    );
                })
                .sorted((a, b) -> Double.compare(b.score(), a.score()))
                .limit(maxResults)
                .toList();
    }

    /**
     * 清除用户所有关键词索引
     */
    public void clearByUserId(String userId) {
        String indexKey = KEYWORD_INDEX_PREFIX + userId;
        redisTemplate.delete(indexKey);
        log.debug("[KeywordRetriever] Cleared keyword index for user {}", userId);
    }

    // ── 内部类型 ──────────────────────────────────────────

    /**
     * 关键词匹配结果
     */
    public record KeywordMatch(
            Long memoryId,
            double score,           // 归一化匹配分数 (0~1)
            int matchCount,         // 匹配的关键词数量
            List<String> matchedKeywords  // 匹配到的关键词列表
    ) {
    }

    /**
     * 批量索引条目
     */
    public record KeywordIndexEntry(Long memoryId, String content) {
    }
}