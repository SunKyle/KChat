package com.example.app.dto.tts;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TtsResult {
    
    private byte[] audio;
    
    @Builder.Default
    private String format = "wav";
    
    private int sampleRate;
    
    private double durationS;
}
