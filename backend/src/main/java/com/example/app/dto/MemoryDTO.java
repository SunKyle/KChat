package com.example.app.dto;

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
}
