package com.example.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPrivacy {
    private Boolean onlineStatus;
    private Boolean messageHistory;
    private Boolean readReceipts;
    private Boolean typingIndicator;
}
