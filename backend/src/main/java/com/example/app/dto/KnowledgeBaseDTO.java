package com.example.app.dto;

import com.example.app.entity.KnowledgeBase;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeBaseDTO {
    private String id;
    private String userId;
    private String name;
    private String description;
    private String datasetName;
    private Integer documentCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static KnowledgeBaseDTO from(KnowledgeBase kb) {
        return KnowledgeBaseDTO.builder()
                .id(kb.getId())
                .userId(kb.getUserId())
                .name(kb.getName())
                .description(kb.getDescription())
                .datasetName(kb.getDatasetName())
                .documentCount(kb.getDocumentCount())
                .createdAt(kb.getCreatedAt())
                .updatedAt(kb.getUpdatedAt())
                .build();
    }
}
