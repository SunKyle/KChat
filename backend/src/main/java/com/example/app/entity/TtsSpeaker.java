package com.example.app.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "tts_speakers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TtsSpeaker {

    @Id
    @Column(name = "spk_id", length = 100)
    private String spkId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "prompt_text", length = 500)
    private String promptText;

    @Column(name = "prompt_wav_path")
    private String promptWavPath;

    @Column(name = "owner_user_id", length = 36)
    @Builder.Default
    private String ownerUserId = "default";

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String source = "zero_shot";

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
