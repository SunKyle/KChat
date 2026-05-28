package com.example.app.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "memory.extractor")
@Data
public class MemoryExtractorConfig {

    private boolean enabled = true;

    private int messageThreshold = 1;

    private int minConfidence = 30;

    private int minImportance = 3;

    private long idleTimeoutMinutes = 10;

    private boolean autoExtractEnabled = true;
}