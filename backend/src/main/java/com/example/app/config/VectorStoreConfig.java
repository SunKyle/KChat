package com.example.app.config;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
@Slf4j
public class VectorStoreConfig {

    @Value("${memory.long-term.vector-dimension:384}")
    private int vectorDimension;

    @Value("${memory.long-term.similarity-threshold:0.5}")
    private double similarityThreshold;

    @Value("${memory.long-term.min-importance:3}")
    private int minImportance;

    @Bean
    public EmbeddingModel embeddingModel() {
        return new EmbeddingModel() {
            @Override
            public Response<Embedding> embed(String text) {
                float[] embedding = new float[vectorDimension];
                int hash = text.hashCode();
                for (int i = 0; i < embedding.length; i++) {
                    embedding[i] = (float) ((hash + i * 97) % 1000) / 500.0f - 1.0f;
                }
                return Response.from(new Embedding(embedding));
            }

            @Override
            public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
                List<Embedding> embeddings = new ArrayList<>();
                for (TextSegment segment : textSegments) {
                    embeddings.add(embed(segment.text()).content());
                }
                return Response.from(embeddings);
            }
        };
    }

    public double getSimilarityThreshold() {
        return similarityThreshold;
    }

    public int getVectorDimension() {
        return vectorDimension;
    }

    public int getMinImportance() {
        return minImportance;
    }
}