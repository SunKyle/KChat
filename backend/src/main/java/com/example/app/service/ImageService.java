package com.example.app.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.*;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class ImageService {

    @Value("${app.image.upload-dir:uploads/images}")
    private String uploadDir;

    @Value("${app.image.base-url:http://localhost:8080/api/images/}")
    private String baseUrl;

    @PostConstruct
    public void init() {
        try {
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
                log.info("Created image upload directory: {}", uploadPath.toAbsolutePath());
            }
        } catch (IOException e) {
            log.error("Failed to create upload directory", e);
            throw new RuntimeException("Failed to create upload directory", e);
        }
    }

    public String uploadImage(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Image file cannot be empty");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = getFileExtension(originalFilename);
        
        String filename = UUID.randomUUID().toString() + extension;
        Path filePath = Paths.get(uploadDir, filename);

        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        log.info("Image uploaded: {}", filename);

        return baseUrl + filename;
    }

    public void deleteImage(String filename) throws IOException {
        Path filePath = Paths.get(uploadDir, filename);
        if (Files.exists(filePath)) {
            Files.delete(filePath);
            log.info("Image deleted: {}", filename);
        }
    }

    public byte[] getImage(String filename) throws IOException {
        Path filePath = Paths.get(uploadDir, filename);
        if (!Files.exists(filePath)) {
            throw new IOException("Image not found: " + filename);
        }
        return Files.readAllBytes(filePath);
    }

    public List<String> listImages() throws IOException {
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            return List.of();
        }
        try (var stream = Files.list(uploadPath)) {
            return stream
                    .filter(Files::isRegularFile)
                    .map(path -> baseUrl + path.getFileName().toString())
                    .toList();
        }
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return ".png";
        }
        return filename.substring(filename.lastIndexOf("."));
    }
}