package com.example.app.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "multimodal")
public class MultimodalProperties {

    /** Planner 模型，为空时使用默认 Ollama 模型 */
    private String plannerModel = "";
    /** 图片理解模型，为空时从已配置模型能力中挑选 */
    private String visionModel = "";
    /** 文生图模型，为空时从已配置模型能力中挑选 */
    private String imageModel = "";
    /** 文本回答模型，为空时使用默认模型 */
    private String textModel = "";
    /** 单次 Auto 模式最大执行步骤数 */
    private int maxSteps = 5;
}
