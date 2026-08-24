package com.example.app.controller;

import com.example.app.dto.CreateKnowledgeBaseRequest;
import com.example.app.dto.KnowledgeBaseDTO;
import com.example.app.dto.KnowledgeDocumentDTO;
import com.example.app.service.KnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库管理 REST API。
 *
 * <p>端点：
 * <ul>
 *   <li>POST   /api/knowledge-bases              — 创建知识库</li>
 *   <li>GET    /api/knowledge-bases              — 列表</li>
 *   <li>GET    /api/knowledge-bases/{id}         — 详情</li>
 *   <li>PUT    /api/knowledge-bases/{id}         — 更新</li>
 *   <li>DELETE /api/knowledge-bases/{id}         — 删除</li>
 *   <li>POST   /api/knowledge-bases/{id}/documents    — 上传文档</li>
 *   <li>GET    /api/knowledge-bases/{id}/documents    — 文档列表</li>
 *   <li>DELETE /api/knowledge-bases/{id}/documents/{docId} — 删除文档</li>
 *   <li>GET    /api/knowledge-bases/{id}/documents/{docId}/status — 处理状态</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/knowledge-bases")
@RequiredArgsConstructor
@Slf4j
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    // ── 知识库 CRUD ──────────────────────────────────────────

    @PostMapping
    public ResponseEntity<KnowledgeBaseDTO> create(
            @RequestParam String userId,
            @RequestBody CreateKnowledgeBaseRequest request) {
        log.info("[KBController] Create KB: user={}, name={}", userId, request.getName());
        return ResponseEntity.ok(knowledgeBaseService.create(userId, request));
    }

    @GetMapping
    public ResponseEntity<List<KnowledgeBaseDTO>> list(@RequestParam String userId) {
        return ResponseEntity.ok(knowledgeBaseService.listByUser(userId));
    }

    @GetMapping("/{kbId}")
    public ResponseEntity<KnowledgeBaseDTO> getById(
            @RequestParam String userId,
            @PathVariable String kbId) {
        return ResponseEntity.ok(knowledgeBaseService.getById(userId, kbId));
    }

    @PutMapping("/{kbId}")
    public ResponseEntity<KnowledgeBaseDTO> update(
            @RequestParam String userId,
            @PathVariable String kbId,
            @RequestBody CreateKnowledgeBaseRequest request) {
        log.info("[KBController] Update KB: id={}", kbId);
        return ResponseEntity.ok(knowledgeBaseService.update(userId, kbId, request));
    }

    @DeleteMapping("/{kbId}")
    public ResponseEntity<Void> delete(
            @RequestParam String userId,
            @PathVariable String kbId) {
        log.info("[KBController] Delete KB: id={}", kbId);
        knowledgeBaseService.delete(userId, kbId);
        return ResponseEntity.noContent().build();
    }

    // ── 文档管理 ──────────────────────────────────────────────

    @PostMapping("/{kbId}/documents")
    public ResponseEntity<KnowledgeDocumentDTO> uploadDocument(
            @RequestParam String userId,
            @PathVariable String kbId,
            @RequestParam("file") MultipartFile file) {
        log.info("[KBController] Upload document: kb={}, file={}, size={}",
                kbId, file.getOriginalFilename(), file.getSize());
        try {
            return ResponseEntity.ok(knowledgeBaseService.uploadDocument(userId, kbId, file));
        } catch (Exception e) {
            log.error("[KBController] Upload failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{kbId}/documents")
    public ResponseEntity<List<KnowledgeDocumentDTO>> listDocuments(
            @RequestParam String userId,
            @PathVariable String kbId) {
        return ResponseEntity.ok(knowledgeBaseService.listDocuments(userId, kbId));
    }

    @DeleteMapping("/{kbId}/documents/{docId}")
    public ResponseEntity<Void> deleteDocument(
            @RequestParam String userId,
            @PathVariable String kbId,
            @PathVariable String docId) {
        log.info("[KBController] Delete document: kb={}, doc={}", kbId, docId);
        knowledgeBaseService.deleteDocument(userId, kbId, docId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 重新索引知识库：清空 Cognee dataset 后重灌全部文档（异步）。
     * 用于修复历史文档向量索引缺失（如后台 remember 未完成导致检索不到正文）。
     * 触发后立即返回 202，可轮询文档状态查看进度。
     */
    @PostMapping("/{kbId}/reindex")
    public ResponseEntity<Void> reindexKb(
            @RequestParam String userId,
            @PathVariable String kbId) {
        log.info("[KBController] Reindex KB: kb={}, user={}", kbId, userId);
        knowledgeBaseService.reindexKb(userId, kbId);
        return ResponseEntity.accepted().build();
    }

    /**
     * 重新索引当前用户的所有知识库（异步逐个重建）。
     *
     * @return 触发的知识库数量
     */
    @PostMapping("/reindex-all")
    public ResponseEntity<Map<String, Integer>> reindexAll(@RequestParam String userId) {
        int count = knowledgeBaseService.reindexAll(userId);
        log.info("[KBController] Reindex all KBs: user={}, count={}", userId, count);
        return ResponseEntity.accepted().body(Map.of("triggered", count));
    }

    @GetMapping("/{kbId}/documents/{docId}/status")
    public ResponseEntity<KnowledgeDocumentDTO> getDocumentStatus(
            @RequestParam String userId,
            @PathVariable String kbId,
            @PathVariable String docId) {
        return ResponseEntity.ok(knowledgeBaseService.getDocumentStatus(userId, kbId, docId));
    }

    /**
     * 下载原始文档文件。
     *
     * @param inline 是否以内联方式展示（true 直接在浏览器打开，false 触发附件下载）。默认 false。
     */
    @GetMapping("/{kbId}/documents/{docId}/download")
    public ResponseEntity<byte[]> downloadDocument(
            @RequestParam String userId,
            @PathVariable String kbId,
            @PathVariable String docId,
            @RequestParam(required = false, defaultValue = "false") Boolean inline) {
        try {
            KnowledgeDocumentDTO docStatus = knowledgeBaseService.getDocumentStatus(userId, kbId, docId);
            AbstractMap.SimpleEntry<byte[], String> result =
                    knowledgeBaseService.downloadDocument(userId, kbId, docId);

            byte[] data = result.getKey();
            String contentType = result.getValue();

            MediaType mediaType;
            try {
                mediaType = MediaType.parseMediaType(contentType);
            } catch (Exception e) {
                mediaType = MediaType.APPLICATION_OCTET_STREAM;
            }

            // 中文文件名兼容（RFC 5987）
            String encodedFileName = URLEncoder.encode(docStatus.getFileName(), StandardCharsets.UTF_8)
                    .replaceAll("\\+", "%20");

            String disposition = (inline ? "inline" : "attachment")
                    + "; filename=\"" + encodedFileName + "\""
                    + "; filename*=UTF-8''" + encodedFileName;

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .contentLength(data.length)
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                    .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                    .body(data);
        } catch (IllegalArgumentException | SecurityException e) {
            log.warn("[KBController] Download denied: kb={}, doc={}, error={}", kbId, docId, e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("[KBController] Download failed: kb={}, doc={}", kbId, docId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
