
package com.example.app.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
}
