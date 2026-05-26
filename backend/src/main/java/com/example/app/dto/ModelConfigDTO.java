package com.example.app.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelConfigDTO {

    private Long id;

    @NotBlank(message = "名称不能为空")
    private String name;

    @NotBlank(message = "模型ID不能为空")
    private String modelId;

    @NotBlank(message = "API地址不能为空")
    private String baseUrl;

    @NotBlank(message = "API Key不能为空")
    private String apiKey;

    private Boolean enabled;
}