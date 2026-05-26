package com.example.app.controller;

import com.example.app.service.ImageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/images")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class ImageController {

    private final ImageService imageService;

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadImage(@RequestParam("image") MultipartFile file) {
        try {
            String url = imageService.uploadImage(file);
            Map<String, String> response = new HashMap<>();
            response.put("url", url);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to upload image", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{filename}")
    public ResponseEntity<byte[]> getImage(@PathVariable String filename) {
        try {
            byte[] image = imageService.getImage(filename);
            String contentType = getContentType(filename);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(image);
        } catch (IOException e) {
            log.error("Failed to get image: {}", filename, e);
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{filename}")
    public ResponseEntity<Void> deleteImage(@PathVariable String filename) {
        try {
            imageService.deleteImage(filename);
            return ResponseEntity.noContent().build();
        } catch (IOException e) {
            log.error("Failed to delete image: {}", filename, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private String getContentType(String filename) {
        if (filename.toLowerCase().endsWith(".png")) {
            return "image/png";
        } else if (filename.toLowerCase().endsWith(".jpg") || filename.toLowerCase().endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (filename.toLowerCase().endsWith(".gif")) {
            return "image/gif";
        } else if (filename.toLowerCase().endsWith(".webp")) {
            return "image/webp";
        } else if (filename.toLowerCase().endsWith(".svg")) {
            return "image/svg+xml";
        }
        return "application/octet-stream";
    }
}