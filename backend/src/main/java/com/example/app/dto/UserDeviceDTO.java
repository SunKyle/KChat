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
public class UserDeviceDTO {
    private String id;
    private String name;
    private String type;
    private String ipAddress;
    private String location;
    private LocalDateTime lastActive;
}
