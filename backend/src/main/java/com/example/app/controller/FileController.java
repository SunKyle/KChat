package com.example.app.controller;

import com.example.app.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 文件上传与管理接口
 *
 * <p>提供文档文件的上传端点，支持 PDF / Word / Excel / PPT / TXT / Markdown 等。
 * 上传后返回 fileId，agent 可通过 {@code FileParseTool.parseFile(fileId)} 解析内容。
 */
@Slf4j
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    /**
     * 上传文件。
     * 前端使用 multipart/form-data，字段名 "file"。
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            String fileId = fileService.uploadFile(file);
            Map<String, Object> info = fileService.getFileInfo(fileId);
            log.info("File uploaded via API: fileId={}, fileName={}", fileId, info.get("fileName"));
            return ResponseEntity.ok(info);
        } catch (IllegalArgumentException e) {
            log.warn("File upload rejected: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to upload file", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "文件上传失败: " + e.getMessage()));
        }
    }

    /**
     * 获取文件信息。
     */
    @GetMapping("/{fileId}/info")
    public ResponseEntity<Map<String, Object>> getFileInfo(@PathVariable String fileId) {
        try {
            return ResponseEntity.ok(fileService.getFileInfo(fileId));
        } catch (IOException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 列出所有已上传文件。
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listFiles() {
        try {
            return ResponseEntity.ok(fileService.listFiles());
        } catch (IOException e) {
            log.error("Failed to list files", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 删除文件。
     */
    @DeleteMapping("/{fileId}")
    public ResponseEntity<Void> deleteFile(@PathVariable String fileId) {
        try {
            fileService.deleteFile(fileId);
            return ResponseEntity.noContent().build();
        } catch (IOException e) {
            log.error("Failed to delete file: {}", fileId, e);
            return ResponseEntity.notFound().build();
        }
    }
}
