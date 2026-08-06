package com.example.app.dto.tts;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpeakerVo {
    
    private String spkId;
    
    private String name;
    
    private String promptText;
    
    private String source;
    
    private String ownerUserId;
    
    private LocalDateTime createdAt;
}
