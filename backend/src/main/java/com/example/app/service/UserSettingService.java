
package com.example.app.service;

import com.example.app.entity.UserSetting;
import com.example.app.repository.UserSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserSettingService {

    private final UserSettingRepository repository;

    public UserSetting getOrCreate(String userId) {
        return repository.findByUserId(userId)
                .orElseGet(() -> createDefaultSetting(userId));
    }

@Transactional
    public UserSetting createDefaultSetting(String userId) {
        UserSetting setting = UserSetting.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .theme("light")
                .memoryEnable(true)
                .defaultModel("llama3")
                .contextSize(10)
                .autoTitle(true)
                .build();
        repository.save(setting);
        log.info("Created default settings for user: {}", userId);
        return setting;
    }

    @Transactional
    public UserSetting update(String userId, UserSetting updateSetting) {
        UserSetting setting = getOrCreate(userId);
        
        if (updateSetting.getTheme() != null) {
            setting.setTheme(updateSetting.getTheme());
        }
        if (updateSetting.getMemoryEnable() != null) {
            setting.setMemoryEnable(updateSetting.getMemoryEnable());
        }
        if (updateSetting.getDefaultModel() != null) {
            setting.setDefaultModel(updateSetting.getDefaultModel());
        }
        if (updateSetting.getContextSize() != null) {
            setting.setContextSize(updateSetting.getContextSize());
        }
        if (updateSetting.getAutoTitle() != null) {
            setting.setAutoTitle(updateSetting.getAutoTitle());
        }
        if (updateSetting.getToolModels() != null) {
            setting.setToolModels(updateSetting.getToolModels());
        }
        
        repository.save(setting);
        log.info("Updated settings for user: {}", userId);
        return setting;
    }

    /**
     * 查询某用户在工具箱为指定工具配置的默认模型（name:modelId）。
     * 未配置返回 null，工具据此回退到自动选择。
     */
    public String getToolModel(String userId, String toolName) {
        if (userId == null || toolName == null) {
            return null;
        }
        UserSetting setting = repository.findByUserId(userId).orElse(null);
        if (setting == null || setting.getToolModels() == null) {
            return null;
        }
        return setting.getToolModels().get(toolName);
    }

    @Transactional
    public void delete(String userId) {
        repository.findByUserId(userId).ifPresent(setting -> {
            repository.delete(setting);
            log.info("Deleted settings for user: {}", userId);
        });
    }

    public UserSetting getById(String id) {
        return repository.findById(id).orElse(null);
    }
}
