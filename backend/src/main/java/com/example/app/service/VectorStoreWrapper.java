package com.example.app.service;

import com.example.app.config.VectorStoreConfig;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class VectorStoreWrapper {

    private final EmbeddingModel embeddingModel;
    private final VectorStoreConfig vectorStoreConfig;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String KEY_PREFIX = "memory:embedding:";
    private static final String INDEX_KEY = "memory:index:";

    public void add(String userId, String content, Long memoryId) {
        try {
            log.info("[Embedding] Generating embedding for memory {} (user: {})", memoryId, userId);
            Embedding embedding = embeddingModel.embed(content).content();
            float[] vector = embedding.vector();
            log.info("[Embedding] Generated embedding with dimension: {}", vector.length);

            String key = KEY_PREFIX + userId + ":" + memoryId;
            String indexKey = INDEX_KEY + userId;

            redisTemplate.opsForValue().set(key, vector);
            redisTemplate.opsForSet().add(indexKey, memoryId.toString());

            log.info("[Embedding] Added embedding for memory {} (user: {})", memoryId, userId);
        } catch (Exception e) {
            log.error("[Embedding] Failed to add embedding: {}", e.getMessage(), e);
        }
    }

    public void addBatch(String userId, List<MemoryEmbeddingPair> pairs) {
        try {
            String indexKey = INDEX_KEY + userId;
            for (MemoryEmbeddingPair pair : pairs) {
                Embedding embedding = embeddingModel.embed(pair.content()).content();
                String key = KEY_PREFIX + userId + ":" + pair.memoryId();
                redisTemplate.opsForValue().set(key, embedding.vector());
                redisTemplate.opsForSet().add(indexKey, pair.memoryId().toString());
            }
            log.debug("Added {} embeddings for user {}", pairs.size(), userId);
        } catch (Exception e) {
            log.warn("Failed to add batch embeddings: {}", e.getMessage());
        }
    }

    public List<Long> search(String userId, String query, int topK) {
        try {
            log.info("[Memory Retrieve] Searching vector store - userId: {}, query: '{}', topK: {}",
                    userId, query, topK);

            Embedding queryEmbedding = embeddingModel.embed(query).content();
            float[] queryVector = queryEmbedding.vector();
            log.info("[Memory Retrieve] Generated query embedding with dimension: {}", queryVector.length);

            String indexKey = INDEX_KEY + userId;
            Set<Object> memoryIdSet = redisTemplate.opsForSet().members(indexKey);

            if (memoryIdSet == null || memoryIdSet.isEmpty()) {
                log.info("[Memory Retrieve] No memories found in index for user: {}", userId);
                return new ArrayList<>();
            }

            log.info("[Memory Retrieve] Found {} memories in index for user: {}", memoryIdSet.size(), userId);

            List<ScoredMemory> scoredMemories = new ArrayList<>();
            for (Object obj : memoryIdSet) {
                try {
                    Long memoryId = Long.parseLong(obj.toString());
                    String key = KEY_PREFIX + userId + ":" + memoryId;
                    Object vectorObj = redisTemplate.opsForValue().get(key);

                    if (vectorObj instanceof float[]) {
                        float[] vector = (float[]) vectorObj;
                        double similarity = cosineSimilarity(queryVector, vector);
                        log.debug("[Memory Retrieve] Memory {} similarity: {}", memoryId, similarity);
                        if (similarity >= vectorStoreConfig.getSimilarityThreshold()) {
                            scoredMemories.add(new ScoredMemory(memoryId, similarity));
                        }
                    }
                } catch (NumberFormatException e) {
                    log.warn("[Memory Retrieve] Invalid memory ID format: {}", obj);
                }
            }

            scoredMemories.sort((a, b) -> Double.compare(b.similarity(), a.similarity()));
            List<Long> results = scoredMemories.stream()
                    .limit(topK)
                    .map(ScoredMemory::memoryId)
                    .toList();

            log.info("[Memory Retrieve] Found {} matching memories (threshold: {})",
                    results.size(), vectorStoreConfig.getSimilarityThreshold());
            return results;
        } catch (Exception e) {
            log.error("[Memory Retrieve] Failed to search vector store: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    private double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length)
            return 0.0;

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        if (normA == 0 || normB == 0)
            return 0.0;
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    public void remove(String userId, Long memoryId) {
        try {
            String key = KEY_PREFIX + userId + ":" + memoryId;
            String indexKey = INDEX_KEY + userId;

            redisTemplate.delete(key);
            redisTemplate.opsForSet().remove(indexKey, memoryId.toString());

            log.debug("Removed embedding for memory {} (user: {})", memoryId, userId);
        } catch (Exception e) {
            log.warn("Failed to remove embedding: {}", e.getMessage());
        }
    }

    public void removeByUserId(String userId) {
        try {
            String indexKey = INDEX_KEY + userId;
            Set<Object> memoryIdSet = redisTemplate.opsForSet().members(indexKey);

            if (memoryIdSet != null) {
                for (Object obj : memoryIdSet) {
                    try {
                        Long memoryId = Long.parseLong(obj.toString());
                        String key = KEY_PREFIX + userId + ":" + memoryId;
                        redisTemplate.delete(key);
                    } catch (NumberFormatException e) {
                        log.warn("Invalid memory ID format: {}", obj);
                    }
                }
                redisTemplate.delete(indexKey);
            }
            log.debug("Removed all embeddings for user {}", userId);
        } catch (Exception e) {
            log.warn("Failed to remove embeddings for user {}: {}", userId, e.getMessage());
        }
    }

    public record MemoryEmbeddingPair(Long memoryId, String content) {
    }

    private record ScoredMemory(Long memoryId, double similarity) {
    }
}