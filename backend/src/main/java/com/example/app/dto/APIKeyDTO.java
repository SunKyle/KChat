package com.example.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class APIKeyDTO {
    private String id;
    private String name;
    private String key;
    private LocalDateTime createdAt;
    private LocalDateTime lastUsed;
    private List<String> scopes;
}
