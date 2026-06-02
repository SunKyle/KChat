package com.example.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileDTO {
    private String id;
    private String nickname;
    private String avatar;
    private String email;
    private String bio;
    private UserPreferences preferences;
    private UserPrivacy privacy;
    private List<APIKeyDTO> apiKeys;
    private List<UserDeviceDTO> devices;
}
