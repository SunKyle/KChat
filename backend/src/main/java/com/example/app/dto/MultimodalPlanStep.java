package com.example.app.dto;

public record MultimodalPlanStep(
        String type,
        String prompt,
        String text,
        Integer targetImage
) {
}
