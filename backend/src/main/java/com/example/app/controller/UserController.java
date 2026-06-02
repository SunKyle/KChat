package com.example.app.controller;

import com.example.app.dto.*;
import com.example.app.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class UserController {

    private final UserProfileService userProfileService;

    @GetMapping("/profile")
    public ResponseEntity<UserProfileDTO> getProfile(@RequestParam String userId) {
        UserProfileDTO profile = userProfileService.getProfile(userId);
        return ResponseEntity.ok(profile);
    }

    @PutMapping("/profile")
    public ResponseEntity<UserProfileDTO> updateProfile(
            @RequestParam String userId,
            @RequestBody UpdateProfileRequest request) {
        UserProfileDTO profile = userProfileService.updateProfile(userId, request);
        return ResponseEntity.ok(profile);
    }

    @PutMapping("/preferences")
    public ResponseEntity<UserProfileDTO> updatePreferences(
            @RequestParam String userId,
            @RequestBody UpdatePreferencesRequest request) {
        UserProfileDTO profile = userProfileService.updatePreferences(userId, request);
        return ResponseEntity.ok(profile);
    }

    @PutMapping("/privacy")
    public ResponseEntity<UserProfileDTO> updatePrivacy(
            @RequestParam String userId,
            @RequestBody UpdatePrivacyRequest request) {
        UserProfileDTO profile = userProfileService.updatePrivacy(userId, request);
        return ResponseEntity.ok(profile);
    }

    @GetMapping("/api-keys")
    public ResponseEntity<List<APIKeyDTO>> getAPIKeys(@RequestParam String userId) {
        List<APIKeyDTO> apiKeys = userProfileService.getAPIKeys(userId);
        return ResponseEntity.ok(apiKeys);
    }

    @PostMapping("/api-keys")
    public ResponseEntity<APIKeyDTO> createAPIKey(
            @RequestParam String userId,
            @RequestBody CreateAPIKeyRequest request) {
        APIKeyDTO apiKey = userProfileService.createAPIKey(userId, request);
        return ResponseEntity.ok(apiKey);
    }

    @DeleteMapping("/api-keys/{keyId}")
    public ResponseEntity<Void> deleteAPIKey(
            @RequestParam String userId,
            @PathVariable String keyId) {
        userProfileService.deleteAPIKey(userId, keyId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/devices/{deviceId}")
    public ResponseEntity<Void> deleteDevice(
            @RequestParam String userId,
            @PathVariable String deviceId) {
        userProfileService.deleteDevice(userId, deviceId);
        return ResponseEntity.noContent().build();
    }
}
