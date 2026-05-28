package com.example.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryRecallRequest {

    private String userId;
    private String query;
    
    @Builder.Default
    private Integer topK = 5;
    
    private List<String> types;
}