package com.example.app.dto;

/**
 * 通用产物记录，用于在 LLM 响应中携带非文本产物（如图片、文件等）。
 *
 * 与多模态规划无关，仅作为 Message 持久化和 API 响应的载体。
 */
public record Artifact(String type, String url, String text) {

    /**
     * 创建图片类型的产物便捷方法。
     */
    public static Artifact image(String url, String prompt) {
        return new Artifact("image", url, prompt);
    }
}
