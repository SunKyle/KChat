
package com.example.app.controller;

import com.example.app.dto.UserSettingDTO;
import com.example.app.entity.UserSetting;
import com.example.app.service.UserSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class UserSettingController {

    private final UserSettingService userSettingService;

    @GetMapping("/{userId}")
    public ResponseEntity<UserSettingDTO> getSettings(@PathVariable String userId) {
        UserSetting setting = userSettingService.getOrCreate(userId);
        return ResponseEntity.ok(toDTO(setting));
    }

    @PutMapping("/{userId}")
    public ResponseEntity<UserSettingDTO> updateSettings(
            @PathVariable String userId,
            @RequestBody UserSettingDTO dto) {
        UserSetting updateSetting = UserSetting.builder()
                .theme(dto.getTheme())
                .memoryEnable(dto.getMemoryEnable())
                .defaultModel(dto.getDefaultModel())
                .contextSize(dto.getContextSize())
                .autoTitle(dto.getAutoTitle())
                .toolModels(dto.getToolModels())
                .enabledTools(dto.getEnabledTools())
                .build();
        
        UserSetting setting = userSettingService.update(userId, updateSetting);
        return ResponseEntity.ok(toDTO(setting));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteSettings(@PathVariable String userId) {
        userSettingService.delete(userId);
        return ResponseEntity.noContent().build();
    }

    private UserSettingDTO toDTO(UserSetting setting) {
        return UserSettingDTO.builder()
                .userId(setting.getUserId())
                .theme(setting.getTheme())
                .memoryEnable(setting.getMemoryEnable())
                .defaultModel(setting.getDefaultModel())
                .contextSize(setting.getContextSize())
                .autoTitle(setting.getAutoTitle())
                .toolModels(setting.getToolModels())
                .enabledTools(setting.getEnabledTools())
                .build();
    }
}
