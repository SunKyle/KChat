package com.example.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MultimodalConfigDTO {

    private String userId;
    private String plannerModel;
    private String visionModel;
    private String imageModel;
    private String textModel;
    private Integer maxSteps;
}
