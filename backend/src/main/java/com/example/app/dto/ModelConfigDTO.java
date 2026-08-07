package com.example.app.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

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

    private String apiKey;

    private String type;

    private String category;

    private Boolean enabled;

    private List<String> capabilities;
}
