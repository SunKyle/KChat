package com.example.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReminderDTO {
    private String id;
    private String userId;
    private String title;
    private String description;
    private LocalDateTime remindAt;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime firedAt;
}
