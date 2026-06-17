package com.example.app.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 敏感信息脱敏组件
 * 
 * 负责识别并脱敏用户输入中的敏感信息，防止信息泄露
 */
@Component
@Slf4j
public class SensitiveFilter {

    /**
     * 脱敏替换字符串
     */
    private static final String MASK = "***";

    /**
     * 敏感信息模式列表
     */
    private static final List<Pattern> SENSITIVE_PATTERNS = Arrays.asList(
            // 中国大陆手机号 (11位)
            Pattern.compile("(?<!\\d)(1[3-9]\\d{9})(?!\\d)"),
            
            // 中国大陆身份证号 (18位)
            Pattern.compile("(?<!\\d)([1-9]\\d{5}(19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[0-9Xx])(?!\\d)"),
            
            // 邮箱地址
            Pattern.compile("([a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})"),
            
            // 银行卡号 (16-19位)
            Pattern.compile("(?<!\\d)(\\d{4}[- ]?\\d{4}[- ]?\\d{4}[- ]?\\d{4,7})(?!\\d)"),
            
            // 护照号 (以G、E、P开头，8位)
            Pattern.compile("(?<!\\d)([GEP]\\d{8})(?!\\d)"),
            
            // 车牌号 (普通民用)
            Pattern.compile("([京津沪渝冀豫云辽黑湘皖鲁新苏浙赣鄂桂甘晋蒙陕吉闽贵粤青藏川宁琼使领][A-Z][A-Z0-9]{5})"),
            
            // IPv4 地址
            Pattern.compile("((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)"),
            
            // URL (http/https)
            Pattern.compile("https?://[\\w\\-]+(\\.[\\w\\-]+)+[/#?]?.*"),
            
            // IPv6 地址 (简化匹配)
            Pattern.compile("([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}"),
            
            // 微信号 (以wx开头，6-20位字母数字下划线)
            Pattern.compile("wx[a-zA-Z0-9_]{5,19}"),
            
            // QQ号 (5-11位数字)
            Pattern.compile("(?<!\\d)([1-9]\\d{4,10})(?!\\d)"),
            
            // 邮政编码 (6位数字)
            Pattern.compile("(?<!\\d)([1-9]\\d{5})(?!\\d)")
    );

    /**
     * 脱敏用户输入
     * 
     * @param input 用户输入
     * @return 脱敏后的输入
     */
    public String sanitize(String input) {
        if (input == null) {
            return null;
        }

        String result = input;
        for (Pattern pattern : SENSITIVE_PATTERNS) {
            result = pattern.matcher(result).replaceAll(MASK);
        }

        // 检查是否进行了脱敏
        if (!input.equals(result)) {
            log.info("Sensitive information sanitized in input");
        }

        return result;
    }

    /**
     * 检查输入是否包含敏感信息
     * 
     * @param input 用户输入
     * @return true 如果包含敏感信息
     */
    public boolean containsSensitiveInfo(String input) {
        if (input == null) {
            return false;
        }

        for (Pattern pattern : SENSITIVE_PATTERNS) {
            if (pattern.matcher(input).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 脱敏日志内容
     * 
     * @param logMessage 日志消息
     * @return 脱敏后的日志消息
     */
    public String sanitizeLog(String logMessage) {
        return sanitize(logMessage);
    }

    /**
     * 获取脱敏替换字符串
     */
    public String getMask() {
        return MASK;
    }
}