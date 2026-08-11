
package com.example.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSettingDTO {

    private String userId;
    private String theme;
    private Boolean memoryEnable;
    private String defaultModel;
    private Integer contextSize;
    private Boolean autoTitle;
    /** 工具默认模型映射：工具名 → 模型ID。空表示自动选择。 */
    private Map<String, String> toolModels;
    /** 工具启用状态映射：工具名 → 是否启用。空表示全部启用。 */
    private Map<String, Boolean> enabledTools;
}
