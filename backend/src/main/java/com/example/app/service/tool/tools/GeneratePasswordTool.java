package com.example.app.service.tool.tools;

import com.example.app.service.tool.ToolComponent;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * 密码生成工具
 *
 * 生成安全随机密码，支持自定义长度和字符类型。
 * 当用户需要创建强密码时调用。
 */
@Slf4j
@Component
public class GeneratePasswordTool implements ToolComponent {

    private static final String LOWERCASE = "abcdefghijklmnopqrstuvwxyz";
    private static final String UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String DIGITS = "0123456789";
    private static final String SPECIAL = "!@#$%^&*()-_=+[]{}|;:,.<>?";

    private static final SecureRandom RANDOM = new SecureRandom();

    @Tool("""
            生成安全随机密码。
            可指定：
            - length: 密码长度（默认 16，范围 8-128）
            - includeUppercase: 是否包含大写字母（默认 true）
            - includeDigits: 是否包含数字（默认 true）
            - includeSpecial: 是否包含特殊字符（默认 true）
            返回生成的密码及其强度评级。
            适用于：注册账号、更新密码、生成临时凭证等场景。
            """)
    public String generatePassword(
            Integer length,
            Boolean includeUppercase,
            Boolean includeDigits,
            Boolean includeSpecial) {
        int len = (length != null && length >= 8) ? Math.min(length, 128) : 16;
        boolean hasUpper = includeUppercase == null || includeUppercase;
        boolean hasDigits = includeDigits == null || includeDigits;
        boolean hasSpecial = includeSpecial == null || includeSpecial;

        try {
            StringBuilder chars = new StringBuilder(LOWERCASE);
            if (hasUpper) chars.append(UPPERCASE);
            if (hasDigits) chars.append(DIGITS);
            if (hasSpecial) chars.append(SPECIAL);

            String charSet = chars.toString();
            StringBuilder password = new StringBuilder(len);

            // 确保至少包含每种选中的字符类型
            if (hasUpper) password.append(UPPERCASE.charAt(RANDOM.nextInt(UPPERCASE.length())));
            if (hasDigits) password.append(DIGITS.charAt(RANDOM.nextInt(DIGITS.length())));
            if (hasSpecial) password.append(SPECIAL.charAt(RANDOM.nextInt(SPECIAL.length())));

            // 填充剩余长度
            for (int i = password.length(); i < len; i++) {
                password.append(charSet.charAt(RANDOM.nextInt(charSet.length())));
            }

            // 打乱顺序
            char[] pwd = password.toString().toCharArray();
            for (int i = pwd.length - 1; i > 0; i--) {
                int j = RANDOM.nextInt(i + 1);
                char tmp = pwd[i];
                pwd[i] = pwd[j];
                pwd[j] = tmp;
            }

            String result = new String(pwd);

            // 强度评估
            String strength = evaluateStrength(len, hasUpper, hasDigits, hasSpecial);

            log.info("[GeneratePasswordTool] Generated password: length={}, strength={}", len, strength);
            return "密码： " + result + "\n强度： " + strength + "\n长度： " + len + " 位" +
                    (hasUpper ? "\n包含：大写字母" : "") +
                    (hasDigits ? "\n包含：数字" : "") +
                    (hasSpecial ? "\n包含：特殊字符" : "");

        } catch (Exception e) {
            log.error("[GeneratePasswordTool] Error: {}", e.getMessage());
            return "密码生成失败：" + e.getMessage();
        }
    }

    private String evaluateStrength(int length, boolean hasUpper, boolean hasDigits, boolean hasSpecial) {
        int score = 0;
        if (length >= 12) score++;
        if (length >= 16) score++;
        if (hasUpper) score++;
        if (hasDigits) score++;
        if (hasSpecial) score++;
        int types = (hasUpper ? 1 : 0) + (hasDigits ? 1 : 0) + (hasSpecial ? 1 : 0);
        if (types >= 2) score++;

        return switch (score) {
            case 0, 1, 2 -> "弱";
            case 3, 4 -> "中";
            default -> "强";
        };
    }
}