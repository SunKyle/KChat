package com.example.app.dto;

import java.util.Set;

public record ModelCapabilityInfo(
        String model,
        Set<String> capabilities
) {
}
