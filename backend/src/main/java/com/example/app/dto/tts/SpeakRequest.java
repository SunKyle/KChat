package com.example.app.dto.tts;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpeakRequest {
    
    private String text;
    
    private String spkId;
    
    @Builder.Default
    private double speed = 1.0;
}
