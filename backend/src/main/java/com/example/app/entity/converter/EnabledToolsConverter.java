package com.example.app.entity.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.HashMap;
import java.util.Map;

/**
 * 把「工具名 → 是否启用」映射存储为 JSON 文本列。
 *
 * 供工具箱页面控制工具的启用/关闭状态，关闭的工具对 LLM 不可见。
 */
@Converter
public class EnabledToolsConverter implements AttributeConverter<Map<String, Boolean>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(Map<String, Boolean> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Map<String, Boolean> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return new HashMap<>();
        }
        try {
            return MAPPER.readValue(dbData, new TypeReference<Map<String, Boolean>>() {});
        } catch (Exception e) {
            return new HashMap<>();
        }
    }
}