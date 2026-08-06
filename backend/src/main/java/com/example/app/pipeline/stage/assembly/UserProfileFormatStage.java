package com.example.app.pipeline.stage.assembly;

import com.example.app.dto.UserProfileDTO;
import com.example.app.pipeline.ContextPipelineStage;
import com.example.app.pipeline.context.ConversationContext;
import com.example.app.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserProfileFormatStage implements ContextPipelineStage {

    private final UserProfileService userProfileService;

    @Override
    public Phase getPhase() { return Phase.ASSEMBLY; }

    public String getName() {
        return "userProfileFormatStage";
    }

    @Override
    public void execute(ConversationContext ctx) {
        String formatted = "";
        try {
            formatted = formatProfile(userProfileService.getProfile(ctx.getUserId()));
        } catch (Exception e) {
            log.warn("Failed to format user profile: {}", e.getMessage());
        }
        ctx.getAgentState().put(ConversationContext.KEY_FORMATTED_USER_PROFILE, formatted);
    }

    private String formatProfile(UserProfileDTO profile) {
        if (profile == null) {
            return "";
        }

        List<String> lines = new ArrayList<>();
        if (isPresent(profile.getNickname()) && !"用户".equals(profile.getNickname())) {
            lines.add("- 昵称：" + profile.getNickname());
        }
        if (isPresent(profile.getBio())) {
            lines.add("- 简介：" + profile.getBio());
        }
        if (profile.getPreferences() != null && isPresent(profile.getPreferences().getLanguage())) {
            lines.add("- 语言偏好：" + profile.getPreferences().getLanguage());
        }

        if (lines.isEmpty()) {
            return "";
        }
        return "用户档案（可信，由系统维护）：\n" + String.join("\n", lines);
    }

    private boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    @Override
    public int getOrder() {
        return 398;
    }

    @Override
    public boolean isCritical() {
        return false;
    }
}
