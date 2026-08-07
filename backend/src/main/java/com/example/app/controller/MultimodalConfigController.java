package com.example.app.controller;

import com.example.app.dto.MultimodalConfigDTO;
import com.example.app.service.MultimodalConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settings/multimodal")
@RequiredArgsConstructor
public class MultimodalConfigController {

    private final MultimodalConfigService multimodalConfigService;

    @GetMapping("/{userId}")
    public ResponseEntity<MultimodalConfigDTO> getConfig(@PathVariable String userId) {
        return ResponseEntity.ok(multimodalConfigService.getByUserId(userId));
    }

    @PutMapping("/{userId}")
    public ResponseEntity<MultimodalConfigDTO> updateConfig(
            @PathVariable String userId,
            @RequestBody MultimodalConfigDTO dto) {
        return ResponseEntity.ok(multimodalConfigService.update(userId, dto));
    }
}
