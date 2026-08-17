package com.example.app.service;

import com.example.app.dto.*;
import com.example.app.entity.APIKey;
import com.example.app.entity.UserDevice;
import com.example.app.entity.UserProfile;
import com.example.app.repository.APIKeyRepository;
import com.example.app.repository.UserDeviceRepository;
import com.example.app.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserProfileService {

    private final UserProfileRepository userProfileRepository;
    private final APIKeyRepository apiKeyRepository;
    private final UserDeviceRepository userDeviceRepository;
    private final UserSettingService userSettingService;

    public UserProfileDTO getProfile(String userId) {
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseGet(() -> createDefaultProfile(userId));

        List<APIKeyDTO> apiKeys = apiKeyRepository.findByUserId(userId).stream()
                .map(this::toAPIKeyDTO)
                .collect(Collectors.toList());

        List<UserDeviceDTO> devices = userDeviceRepository.findByUserId(userId).stream()
                .map(this::toUserDeviceDTO)
                .collect(Collectors.toList());

        return toUserProfileDTO(profile, apiKeys, devices);
    }

    /**
     * 获取用户语言偏好（轻量级查询，不加载关联数据）
     *
     * @param userId 用户 ID
     * @return 语言代码（如 "zh-CN"），默认 "zh-CN"
     */
    public String getLanguage(String userId) {
        String language = userProfileRepository.findByUserId(userId)
                .map(UserProfile::getLanguage)
                .orElse("zh-CN");
        return (language != null && !language.isBlank()) ? language : "zh-CN";
    }

    @Transactional
    public UserProfile createDefaultProfile(String userId) {
        UserProfile profile = UserProfile.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .nickname("用户")
                .email(userId + "@example.com")
                .language("zh-CN")
                .notificationMessage(true)
                .notificationEmail(false)
                .notificationPush(true)
                .notificationSound(true)
                .onlineStatus(true)
                .messageHistory(true)
                .readReceipts(true)
                .typingIndicator(true)
                .build();
        userProfileRepository.save(profile);
        log.info("Created default profile for user: {}", userId);
        return profile;
    }

    @Transactional
    public UserProfileDTO updateProfile(String userId, UpdateProfileRequest request) {
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseGet(() -> createDefaultProfile(userId));

        if (request.getNickname() != null) {
            profile.setNickname(request.getNickname());
        }
        if (request.getAvatar() != null) {
            profile.setAvatar(request.getAvatar());
        }
        if (request.getEmail() != null) {
            profile.setEmail(request.getEmail());
        }
        if (request.getBio() != null) {
            profile.setBio(request.getBio());
        }

        userProfileRepository.save(profile);
        log.info("Updated profile for user: {}", userId);

        List<APIKeyDTO> apiKeys = apiKeyRepository.findByUserId(userId).stream()
                .map(this::toAPIKeyDTO)
                .collect(Collectors.toList());
        List<UserDeviceDTO> devices = userDeviceRepository.findByUserId(userId).stream()
                .map(this::toUserDeviceDTO)
                .collect(Collectors.toList());

        return toUserProfileDTO(profile, apiKeys, devices);
    }

    @Transactional
    public UserProfileDTO updatePreferences(String userId, UpdatePreferencesRequest request) {
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseGet(() -> createDefaultProfile(userId));

        if (request.getLanguage() != null) {
            profile.setLanguage(request.getLanguage());
        }
        // theme 由 UserSetting 管理，此处不处理
        if (request.getNotifications() != null) {
            NotificationSettings notifications = request.getNotifications();
            if (notifications.getMessage() != null) {
                profile.setNotificationMessage(notifications.getMessage());
            }
            if (notifications.getEmail() != null) {
                profile.setNotificationEmail(notifications.getEmail());
            }
            if (notifications.getPush() != null) {
                profile.setNotificationPush(notifications.getPush());
            }
            if (notifications.getSound() != null) {
                profile.setNotificationSound(notifications.getSound());
            }
        }

        userProfileRepository.save(profile);
        log.info("Updated preferences for user: {}", userId);

        List<APIKeyDTO> apiKeys = apiKeyRepository.findByUserId(userId).stream()
                .map(this::toAPIKeyDTO)
                .collect(Collectors.toList());
        List<UserDeviceDTO> devices = userDeviceRepository.findByUserId(userId).stream()
                .map(this::toUserDeviceDTO)
                .collect(Collectors.toList());

        return toUserProfileDTO(profile, apiKeys, devices);
    }

    @Transactional
    public UserProfileDTO updatePrivacy(String userId, UpdatePrivacyRequest request) {
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseGet(() -> createDefaultProfile(userId));

        if (request.getOnlineStatus() != null) {
            profile.setOnlineStatus(request.getOnlineStatus());
        }
        if (request.getMessageHistory() != null) {
            profile.setMessageHistory(request.getMessageHistory());
        }
        if (request.getReadReceipts() != null) {
            profile.setReadReceipts(request.getReadReceipts());
        }
        if (request.getTypingIndicator() != null) {
            profile.setTypingIndicator(request.getTypingIndicator());
        }

        userProfileRepository.save(profile);
        log.info("Updated privacy settings for user: {}", userId);

        List<APIKeyDTO> apiKeys = apiKeyRepository.findByUserId(userId).stream()
                .map(this::toAPIKeyDTO)
                .collect(Collectors.toList());
        List<UserDeviceDTO> devices = userDeviceRepository.findByUserId(userId).stream()
                .map(this::toUserDeviceDTO)
                .collect(Collectors.toList());

        return toUserProfileDTO(profile, apiKeys, devices);
    }

    @Transactional
    public APIKeyDTO createAPIKey(String userId, CreateAPIKeyRequest request) {
        String apiKey = "sk-" + UUID.randomUUID().toString().replace("-", "");

        APIKey entity = APIKey.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .name(request.getName())
                .key(apiKey)
                .scopes(request.getScopes() != null ? String.join(",", request.getScopes()) : "")
                .build();

        apiKeyRepository.save(entity);
        log.info("Created API key for user: {}", userId);

        return toAPIKeyDTO(entity);
    }

    public List<APIKeyDTO> getAPIKeys(String userId) {
        return apiKeyRepository.findByUserId(userId).stream()
                .map(this::toAPIKeyDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteAPIKey(String userId, String keyId) {
        APIKey apiKey = apiKeyRepository.findById(keyId)
                .orElseThrow(() -> new RuntimeException("API key not found"));

        if (!apiKey.getUserId().equals(userId)) {
            throw new RuntimeException("Unauthorized");
        }

        apiKeyRepository.delete(apiKey);
        log.info("Deleted API key {} for user: {}", keyId, userId);
    }

    @Transactional
    public void deleteDevice(String userId, String deviceId) {
        UserDevice device = userDeviceRepository.findById(deviceId)
                .orElseThrow(() -> new RuntimeException("Device not found"));

        if (!device.getUserId().equals(userId)) {
            throw new RuntimeException("Unauthorized");
        }

        userDeviceRepository.delete(device);
        log.info("Deleted device {} for user: {}", deviceId, userId);
    }

    // DTO转换方法

    private UserProfileDTO toUserProfileDTO(UserProfile profile, List<APIKeyDTO> apiKeys, List<UserDeviceDTO> devices) {
        NotificationSettings notifications = NotificationSettings.builder()
                .message(profile.getNotificationMessage())
                .email(profile.getNotificationEmail())
                .push(profile.getNotificationPush())
                .sound(profile.getNotificationSound())
                .build();

        UserPreferences preferences = UserPreferences.builder()
                .theme(userSettingService.getOrCreate(profile.getId()).getTheme())
                .language(profile.getLanguage())
                .notifications(notifications)
                .build();

        UserPrivacy privacy = UserPrivacy.builder()
                .onlineStatus(profile.getOnlineStatus())
                .messageHistory(profile.getMessageHistory())
                .readReceipts(profile.getReadReceipts())
                .typingIndicator(profile.getTypingIndicator())
                .build();

        return UserProfileDTO.builder()
                .id(profile.getId())
                .nickname(profile.getNickname())
                .avatar(profile.getAvatar())
                .email(profile.getEmail())
                .bio(profile.getBio())
                .preferences(preferences)
                .privacy(privacy)
                .apiKeys(apiKeys)
                .devices(devices)
                .build();
    }

    private APIKeyDTO toAPIKeyDTO(APIKey entity) {
        List<String> scopes = entity.getScopes() != null && !entity.getScopes().isEmpty()
                ? Arrays.asList(entity.getScopes().split(","))
                : List.of();

        return APIKeyDTO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .key(entity.getKey())
                .createdAt(entity.getCreatedAt())
                .lastUsed(entity.getLastUsed())
                .scopes(scopes)
                .build();
    }

    private UserDeviceDTO toUserDeviceDTO(UserDevice entity) {
        return UserDeviceDTO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .type(entity.getType())
                .ipAddress(entity.getIpAddress())
                .location(entity.getLocation())
                .lastActive(entity.getLastActive())
                .build();
    }
}
