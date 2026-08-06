package com.example.app.dto.tts;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CosyVoiceHealth {
    
    private String status;
    
    private String modelDir;
    
    private String modelType;
    
    private int sampleRate;
    
    private String device;
    
    private boolean cudaAvailable;
    
    private int speakers;
    
    private int queueSize;
    
    private int concurrency;
}
