package com.example.app.dto;

import com.example.app.entity.LongTermMemory;
import com.example.app.entity.LongTermMemory.MemoryType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryDTO {

    private Long id;
    private String userId;
    private String content;
    private String type;
    private Integer importance;
    private Double confidence;
    private String source;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Double score;

    public static MemoryDTO fromEntity(LongTermMemory entity) {
        return MemoryDTO.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .content(entity.getContent())
                .type(entity.getType().name())
                .importance(entity.getImportance())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .confidence(entity.getConfidence())
                .source(entity.getSource())
                .build();
    }

    public static MemoryDTO fromEntity(LongTermMemory entity, Double score) {
        MemoryDTO dto = fromEntity(entity);
        dto.setScore(score);
        return dto;
    }

    public MemoryType getMemoryType() {
        return type != null ? MemoryType.valueOf(type.toUpperCase()) : null;
    }
}
