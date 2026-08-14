package com.example.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateKnowledgeBaseRequest {

    @NotBlank(message = "知识库名称不能为空")
    @Size(max = 200, message = "知识库名称不能超过200字符")
    private String name;

    @Size(max = 1000, message = "描述不能超过1000字符")
    private String description;
}
