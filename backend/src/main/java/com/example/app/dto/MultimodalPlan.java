package com.example.app.dto;

import java.util.List;

public record MultimodalPlan(
        List<MultimodalPlanStep> steps
) {
}
