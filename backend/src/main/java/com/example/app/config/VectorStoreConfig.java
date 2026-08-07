package com.example.app.config;

import com.example.app.client.OllamaClient;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.NDManager;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.NoopTranslator;
import ai.djl.translate.TranslatorContext;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Configuration
@Slf4j
@RequiredArgsConstructor
public class VectorStoreConfig {

    private final OllamaClient ollamaClient;

    @Value("${memory.long-term.vector-dimension:384}")
    private int vectorDimension;

    @Value("${memory.long-term.similarity-threshold:0.5}")
    private double similarityThreshold;

    @Value("${memory.long-term.min-importance:3}")
    private int minImportance;

    private ZooModel<NDList, NDList> embeddingModel;
    private HuggingFaceTokenizer tokenizer;
    private NDManager ndManager;
    private volatile boolean ollamaEmbeddingUnavailable = false;
    private volatile long lastOllamaEmbeddingAttempt = 0L;

    @Bean
    public EmbeddingModel embeddingModel() {
        try {
            log.info("Loading embedding model: all-MiniLM-L6-v2 (first run downloads ~90MB model + ~200MB engine)...");

            Criteria<NDList, NDList> criteria = Criteria.builder()
                    .setTypes(NDList.class, NDList.class)
                    .optModelUrls("djl://ai.djl.huggingface.pytorch/sentence-transformers/all-MiniLM-L6-v2")
                    .optEngine("PyTorch")
                    .optModelPath(Paths.get("models/embedding"))
                    .optTranslator(new NoopTranslator())
                    .build();

            embeddingModel = criteria.loadModel();

            tokenizer = HuggingFaceTokenizer.builder()
                    .optTokenizerPath(Paths.get("models/embedding/tokenizer.json"))
                    .build();

            ndManager = NDManager.newBaseManager();

            log.info("Embedding model loaded (dimension={})", vectorDimension);
        } catch (Exception e) {
            log.warn("Failed to load local embedding model, will try Ollama embeddings: {}",
                    e.getMessage());
            return createFallbackEmbeddingModel();
        }

        return new EmbeddingModel() {
            @Override
            public Response<Embedding> embed(String text) {
                try {
                    var encoding = tokenizer.encode(text);
                    long[] ids = encoding.getIds();
                    long[] mask = encoding.getAttentionMask();

                    NDArray inputIds = ndManager.create(ids).reshape(1, ids.length);
                    NDArray attMask = ndManager.create(mask).reshape(1, mask.length);
                    NDList inputs = new NDList(inputIds, attMask);

                    try (var predictor = embeddingModel.newPredictor()) {
                        NDList output = predictor.predict(inputs);
                        NDArray lastHidden = output.get(0);
                        NDArray pooled = meanPool(lastHidden, attMask);

                        float[] vector = pooled.toFloatArray();
                        normalize(vector);
                        return Response.from(new Embedding(vector));
                    }
                } catch (Exception e) {
                    log.warn("Embedding inference failed: {}", e.getMessage());
                    return fallbackEmbed(text);
                }
            }

            @Override
            public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
                List<Embedding> results = new ArrayList<>();
                for (TextSegment seg : segments) {
                    results.add(embed(seg.text()).content());
                }
                return Response.from(results);
            }
        };
    }

    /** Mean pooling with attention mask: average token vectors, ignoring padding. */
    private NDArray meanPool(NDArray lastHidden, NDArray attentionMask) {
        NDArray mask = attentionMask.expandDims(-1).broadcast(lastHidden.getShape());
        NDArray masked = lastHidden.mul(mask);
        NDArray summed = masked.sum(new int[]{1});
        NDArray counts = mask.sum(new int[]{1}).clip(1e-9, Float.MAX_VALUE);
        return summed.div(counts);
    }

    private void normalize(float[] vector) {
        double norm = 0;
        for (float v : vector) norm += v * v;
        norm = Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < vector.length; i++) vector[i] /= norm;
        }
    }

    private EmbeddingModel createFallbackEmbeddingModel() {
        log.warn("Using Ollama/hash fallback embeddings; install one with: ollama pull locusai/all-minilm-l6-v2");
        return new EmbeddingModel() {
            @Override
            public Response<Embedding> embed(String text) {
                return ollamaEmbedOrFallback(text);
            }
            @Override
            public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
                List<Embedding> results = new ArrayList<>();
                for (TextSegment seg : segments) results.add(embed(seg.text()).content());
                return Response.from(results);
            }
        };
    }

    private Response<Embedding> ollamaEmbedOrFallback(String text) {
        long now = System.currentTimeMillis();
        if (!ollamaEmbeddingUnavailable || now - lastOllamaEmbeddingAttempt >= 60_000L) {
            try {
                float[] vector = ollamaClient.embed(text);
                if (vector.length > 0) {
                    if (vector.length != vectorDimension) {
                        log.warn("Ollama embedding dimension {} does not match configured {}, using hash fallback",
                                vector.length, vectorDimension);
                    } else {
                        ollamaEmbeddingUnavailable = false;
                        return Response.from(new Embedding(vector));
                    }
                }
            } catch (Exception e) {
                log.warn("Ollama embedding failed, using hash fallback: {}", e.getMessage());
            }
            ollamaEmbeddingUnavailable = true;
            lastOllamaEmbeddingAttempt = now;
        }
        return fallbackEmbed(text);
    }

    private Response<Embedding> fallbackEmbed(String text) {
        float[] embedding = new float[vectorDimension];
        int hash = text.hashCode();
        for (int i = 0; i < embedding.length; i++) {
            embedding[i] = (float) ((hash + i * 97) % 1000) / 500.0f - 1.0f;
        }
        return Response.from(new Embedding(embedding));
    }

    @PreDestroy
    public void cleanup() {
        if (embeddingModel != null) embeddingModel.close();
        if (ndManager != null) ndManager.close();
    }

    public double getSimilarityThreshold() { return similarityThreshold; }
    public int getVectorDimension() { return vectorDimension; }
    public int getMinImportance() { return minImportance; }
}
