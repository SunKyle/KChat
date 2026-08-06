package com.example.app.dto.tts;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreviewRequest {
    
    private String text;
    
    private String promptText;
    
    /**
     * base64 编码的 prompt wav 数据（临时试听用，不注册）
     */
    private String promptWavBase64;
    
    @Builder.Default
    private double speed = 1.0;
}
