package com.example.app.service;

import com.example.app.dto.CreateKnowledgeBaseRequest;
import com.example.app.dto.KnowledgeBaseDTO;
import com.example.app.dto.KnowledgeDocumentDTO;
import com.example.app.entity.KnowledgeBase;
import com.example.app.entity.KnowledgeDocument;
import com.example.app.repository.KnowledgeBaseRepository;
import com.example.app.repository.KnowledgeDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 知识库管理服务。
 *
 * <p>职责：
 * <ul>
 *   <li>知识库 CRUD（每个知识库对应一个 Cognee dataset：kb_{id}）</li>
 *   <li>文档上传 + Apache Tika 文本提取</li>
 *   <li>异步将文档内容写入 Cognee 知识图谱（remember with dataset_name）</li>
 *   <li>删除知识库时同步清理 Cognee dataset</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgeBaseService {

    private final KnowledgeBaseRepository kbRepository;
    private final KnowledgeDocumentRepository documentRepository;
    private final CogneeClient cogneeClient;

    /**
     * 自引用代理（@Lazy 避免循环依赖）。
     *
     * <p>同 class 内部直接调用 @Async 方法属于 self-invocation，会绕过 Spring AOP 代理，
     * 导致异步方法同步阻塞（如 reindex 触发前端超时）。必须通过 self.xxx() 走代理，
     * @Async 才会真正生效。
     */
    @Autowired
    @Lazy
    private KnowledgeBaseService self;

    @Value("${kchat.knowledge-base.upload-dir:uploads/knowledge}")
    private String uploadDir;

    /** 缓存绝对路径，避免每次都重新解析 */
    private Path absoluteUploadDir;

    private static final Tika TIKA = new Tika();

    /**
     * 获取上传目录的绝对路径（解析到 backend 进程 cwd 下）。
     * MultipartFile.transferTo(File) 会把相对路径解析到 Tomcat 临时 work dir，
     * 而 Files.createDirectories() 解析到进程 cwd，导致找不到目录。
     * 统一使用绝对路径避免此问题。
     */
    private Path getAbsoluteUploadDir() {
        if (absoluteUploadDir == null) {
            Path relative = Paths.get(uploadDir);
            absoluteUploadDir = relative.isAbsolute()
                    ? relative
                    : Paths.get(System.getProperty("user.dir")).resolve(relative).normalize();
        }
        return absoluteUploadDir;
    }

    /**
     * 归一化知识库 ID：容忍 LLM 误传 "kb_" 前缀（datasetName 风格），还原为纯 UUID。
     * 知识库 ID 在数据库里是纯 UUID（如 01510f33-...），而 Cognee dataset 名是
     * "kb_{id}"。LLM 容易把两者混淆、把带前缀的 datasetName 当 ID 传入，
     * 这里在查询前统一剥离前缀，避免误报「知识库不存在」。
     *
     * @param kbId 可能带 "kb_" 前缀的 ID
     * @return 纯 UUID；若剥离后不是合法 UUID 则保留原值，交给查询层报错
     */
    private String normalizeKbId(String kbId) {
        if (kbId != null && kbId.startsWith("kb_")) {
            String stripped = kbId.substring(3);
            try {
                UUID.fromString(stripped);
                return stripped;
            } catch (IllegalArgumentException ignore) {
                // 不是 UUID，保留原值
            }
        }
        return kbId;
    }

    // ── 知识库 CRUD ──────────────────────────────────────────

    /**
     * 创建知识库。
     */
    public KnowledgeBaseDTO create(String userId, CreateKnowledgeBaseRequest request) {
        String id = UUID.randomUUID().toString();
        KnowledgeBase kb = KnowledgeBase.builder()
                .id(id)
                .userId(userId)
                .name(request.getName())
                .description(request.getDescription())
                .datasetName("kb_" + id)
                .build();
        kb = kbRepository.save(kb);
        log.info("[KnowledgeBase] Created: id={}, name={}, user={}", id, request.getName(), userId);
        return KnowledgeBaseDTO.from(kb);
    }

    /**
     * 获取用户所有知识库。
     */
    public List<KnowledgeBaseDTO> listByUser(String userId) {
        return kbRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(KnowledgeBaseDTO::from)
                .toList();
    }

    /**
     * 获取知识库详情。
     */
    public KnowledgeBaseDTO getById(String userId, String kbId) {
        KnowledgeBase kb = kbRepository.findByIdAndUserId(normalizeKbId(kbId), userId)
                .orElseThrow(() -> new IllegalArgumentException("知识库不存在或无权访问"));
        return KnowledgeBaseDTO.from(kb);
    }

    /**
     * 更新知识库信息。
     */
    public KnowledgeBaseDTO update(String userId, String kbId, CreateKnowledgeBaseRequest request) {
        KnowledgeBase kb = kbRepository.findByIdAndUserId(normalizeKbId(kbId), userId)
                .orElseThrow(() -> new IllegalArgumentException("知识库不存在或无权访问"));
        kb.setName(request.getName());
        kb.setDescription(request.getDescription());
        kb = kbRepository.save(kb);
        log.info("[KnowledgeBase] Updated: id={}", kbId);
        return KnowledgeBaseDTO.from(kb);
    }

    /**
     * 删除知识库（同时删除 Cognee dataset、所有文档记录、以及磁盘上的上传目录）。
     */
    public void delete(String userId, String kbId) {
        KnowledgeBase kb = kbRepository.findByIdAndUserId(normalizeKbId(kbId), userId)
                .orElseThrow(() -> new IllegalArgumentException("知识库不存在或无权访问"));

        // 删除 Cognee dataset
        try {
            cogneeClient.forgetDataset(kb.getDatasetName());
            log.info("[KnowledgeBase] Cognee dataset deleted: {}", kb.getDatasetName());
        } catch (Exception e) {
            log.warn("[KnowledgeBase] Failed to delete Cognee dataset: {}", e.getMessage());
        }

        // 删除磁盘上的上传目录（含所有原始文档文件）
        try {
            Path kbDirPath = getAbsoluteUploadDir().resolve(kbId).normalize();
            if (kbDirPath.startsWith(getAbsoluteUploadDir()) && Files.exists(kbDirPath)) {
                try (var walk = Files.walk(kbDirPath)) {
                    walk.sorted(java.util.Comparator.reverseOrder())
                            .forEach(path -> {
                                try { Files.deleteIfExists(path); } catch (IOException ignored) {}
                            });
                }
            }
        } catch (IOException e) {
            log.warn("[KnowledgeBase] Failed to delete KB upload dir: {}", e.getMessage());
        }

        // 删除所有文档记录
        List<KnowledgeDocument> docs = documentRepository.findByKbIdOrderByCreatedAtDesc(kbId);
        documentRepository.deleteAll(docs);

        // 删除知识库
        kbRepository.delete(kb);
        log.info("[KnowledgeBase] Deleted: id={}, docs={}", kbId, docs.size());
    }

    // ── 文档管理 ──────────────────────────────────────────────

    /**
     * 上传文档到知识库。
     * 同步执行：保存文件 → Tika 提取文本 → 保存文档记录 → 异步写入 Cognee。
     */
    public KnowledgeDocumentDTO uploadDocument(String userId, String kbId, MultipartFile file) throws IOException {
        KnowledgeBase kb = kbRepository.findByIdAndUserId(normalizeKbId(kbId), userId)
                .orElseThrow(() -> new IllegalArgumentException("知识库不存在或无权访问"));

        if (file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }

        String docId = UUID.randomUUID().toString();
        String originalFilename = file.getOriginalFilename();
        String fileType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";

        // 使用绝对路径保存原始文件（统一解析到 backend cwd）
        Path dirPath = getAbsoluteUploadDir().resolve(kbId);
        Files.createDirectories(dirPath);
        // 存储相对路径：kbId/docId_文件名.ext，便于迁移和 URL 组装
        String safeFileName = originalFilename != null ? originalFilename.replaceAll("[^a-zA-Z0-9\\.\\-_\\u4e00-\\u9fa5]", "_") : "file";
        Path filePath = dirPath.resolve(docId + "_" + safeFileName);
        String relativeFilePath = kbId + "/" + docId + "_" + safeFileName;

        // 使用 Files.copy 写入磁盘，避免 MultipartFile.transferTo(File) 的路径解析问题
        try (InputStream is = file.getInputStream()) {
            Files.copy(is, filePath, StandardCopyOption.REPLACE_EXISTING);
        }

        // Apache Tika 提取文本（直接从 multipart stream，避免再次打开磁盘文件）
        String content;
        try (InputStream is = file.getInputStream()) {
            content = TIKA.parseToString(is);
        } catch (Exception e) {
            log.error("[KnowledgeBase] Tika extraction failed for {}: {}", originalFilename, e.getMessage());
            content = "";
        }

        // 创建文档记录
        KnowledgeDocument doc = KnowledgeDocument.builder()
                .id(docId)
                .kbId(kbId)
                .fileName(originalFilename)
                .fileType(fileType)
                .fileSize(file.getSize())
                .content(content)
                .contentLength(content != null ? content.length() : 0)
                .status(KnowledgeDocument.ProcessingStatus.PROCESSING)
                .storedFilePath(relativeFilePath)
                .build();
        doc = documentRepository.save(doc);

        // 更新知识库文档计数
        kb.setDocumentCount(kb.getDocumentCount() + 1);
        kbRepository.save(kb);

        log.info("[KnowledgeBase] Document uploaded: kb={}, doc={}, file={}, chars={}, stored={}",
                kbId, docId, originalFilename, doc.getContentLength(), relativeFilePath);

        // 异步写入 Cognee（走代理，确保 @Async 生效，不阻塞上传请求）
        self.ingestToCogneeAsync(doc.getId(), kb.getDatasetName(), content, originalFilename);

        String downloadUrl = "/api/knowledge-bases/" + kbId + "/documents/" + docId + "/download";
        return KnowledgeDocumentDTO.from(doc, downloadUrl);
    }

    /**
     * 将纯文本内容直接写入知识库（供 Agent Tool 调用，无 MultipartFile）。
     *
     * <p>保存文件 → 保存文档记录 → 异步写入 Cognee。
     * 与 uploadDocument(MultipartFile) 复用同一套持久化链路。
     */
    public KnowledgeDocumentDTO uploadTextDocument(String userId, String kbId,
                                                   String fileName, String content) {
        KnowledgeBase kb = kbRepository.findByIdAndUserId(normalizeKbId(kbId), userId)
                .orElseThrow(() -> new IllegalArgumentException("知识库不存在或无权访问"));

        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("文档内容不能为空");
        }
        if (fileName == null || fileName.isBlank()) {
            fileName = "untitled.txt";
        }

        String docId = UUID.randomUUID().toString();

        // 存一份文本文件到磁盘（与 uploadDocument 同目录结构，保持一致可下载）
        Path dirPath = getAbsoluteUploadDir().resolve(kbId);
        try {
            Files.createDirectories(dirPath);
        } catch (IOException e) {
            throw new RuntimeException("创建知识库目录失败: " + e.getMessage(), e);
        }
        String safeFileName = fileName.replaceAll("[^a-zA-Z0-9\\.\\-_\\u4e00-\\u9fa5]", "_");
        Path filePath = dirPath.resolve(docId + "_" + safeFileName);
        String relativeFilePath = kbId + "/" + docId + "_" + safeFileName;
        try {
            Files.writeString(filePath, content, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("写入文档文件失败: " + e.getMessage(), e);
        }

        KnowledgeDocument doc = KnowledgeDocument.builder()
                .id(docId)
                .kbId(kbId)
                .fileName(fileName)
                .fileType("text/plain")
                .fileSize((long) content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
                .content(content)
                .contentLength(content.length())
                .status(KnowledgeDocument.ProcessingStatus.PROCESSING)
                .storedFilePath(relativeFilePath)
                .build();
        doc = documentRepository.save(doc);

        kb.setDocumentCount(kb.getDocumentCount() + 1);
        kbRepository.save(kb);

        log.info("[KnowledgeBase] Text document uploaded: kb={}, doc={}, file={}, chars={}",
                kbId, docId, fileName, doc.getContentLength());

        // 异步写入 Cognee（走代理，确保 @Async 生效）
        self.ingestToCogneeAsync(doc.getId(), kb.getDatasetName(), content, fileName);

        String downloadUrl = "/api/knowledge-bases/" + kbId + "/documents/" + docId + "/download";
        return KnowledgeDocumentDTO.from(doc, downloadUrl);
    }

    /**
     * 获取知识库下所有文档。
     */
    public List<KnowledgeDocumentDTO> listDocuments(String userId, String kbId) {
        // 验证权限
        kbRepository.findByIdAndUserId(normalizeKbId(kbId), userId)
                .orElseThrow(() -> new IllegalArgumentException("知识库不存在或无权访问"));
        return documentRepository.findByKbIdOrderByCreatedAtDesc(kbId).stream()
                .map(doc -> {
                    String downloadUrl = "/api/knowledge-bases/" + kbId + "/documents/" + doc.getId() + "/download";
                    return KnowledgeDocumentDTO.from(doc, downloadUrl);
                })
                .toList();
    }

    /**
     * 获取文档处理状态。
     */
    public KnowledgeDocumentDTO getDocumentStatus(String userId, String kbId, String docId) {
        kbRepository.findByIdAndUserId(normalizeKbId(kbId), userId)
                .orElseThrow(() -> new IllegalArgumentException("知识库不存在或无权访问"));
        KnowledgeDocument doc = documentRepository.findByIdAndKbId(docId, kbId)
                .orElseThrow(() -> new IllegalArgumentException("文档不存在"));
        String downloadUrl = "/api/knowledge-bases/" + kbId + "/documents/" + docId + "/download";
        return KnowledgeDocumentDTO.from(doc, downloadUrl);
    }

    /**
     * 获取原始文件（用于前端下载/预览）。
     * 返回 byte[] 和文件名，由 Controller 设置 Content-Disposition。
     *
     * @return Pair: left = 文件字节数组，right = Content-Type 推断值
     */
    public java.util.AbstractMap.SimpleEntry<byte[], String> downloadDocument(
            String userId, String kbId, String docId) throws IOException {
        kbRepository.findByIdAndUserId(normalizeKbId(kbId), userId)
                .orElseThrow(() -> new IllegalArgumentException("知识库不存在或无权访问"));
        KnowledgeDocument doc = documentRepository.findByIdAndKbId(docId, kbId)
                .orElseThrow(() -> new IllegalArgumentException("文档不存在"));
        if (doc.getStoredFilePath() == null || doc.getStoredFilePath().isBlank()) {
            throw new IllegalArgumentException("文档文件不存在");
        }
        Path fileOnDisk = getAbsoluteUploadDir().resolve(doc.getStoredFilePath()).normalize();
        if (!Files.exists(fileOnDisk)) {
            throw new IllegalArgumentException("文档文件不存在于磁盘");
        }
        // 路径穿越防护（确保文件仍在 uploads 目录内）
        if (!fileOnDisk.startsWith(getAbsoluteUploadDir())) {
            throw new SecurityException("非法路径访问");
        }
        byte[] data = Files.readAllBytes(fileOnDisk);
        String contentType = doc.getFileType() != null ? doc.getFileType() : "application/octet-stream";
        return new java.util.AbstractMap.SimpleEntry<>(data, contentType);
    }

    /**
     * 删除文档。
     */
    public void deleteDocument(String userId, String kbId, String docId) {
        KnowledgeBase kb = kbRepository.findByIdAndUserId(normalizeKbId(kbId), userId)
                .orElseThrow(() -> new IllegalArgumentException("知识库不存在或无权访问"));
        KnowledgeDocument doc = documentRepository.findByIdAndKbId(docId, kbId)
                .orElseThrow(() -> new IllegalArgumentException("文档不存在"));

        // 先取出 Cognee data_id（删除记录后实体不再可查）
        String cogneeDataId = doc.getCogneeDataId();

        // 同时删除磁盘上的原始文件
        if (doc.getStoredFilePath() != null && !doc.getStoredFilePath().isBlank()) {
            try {
                Path fileOnDisk = getAbsoluteUploadDir().resolve(doc.getStoredFilePath()).normalize();
                if (fileOnDisk.startsWith(getAbsoluteUploadDir()) && Files.exists(fileOnDisk)) {
                    Files.deleteIfExists(fileOnDisk);
                }
            } catch (IOException e) {
                log.warn("[KnowledgeBase] Failed to delete file on disk for doc={}: {}", docId, e.getMessage());
            }
        }

        documentRepository.delete(doc);

        // 更新文档计数
        kb.setDocumentCount(Math.max(0, kb.getDocumentCount() - 1));
        kbRepository.save(kb);

        log.info("[KnowledgeBase] Document deleted: kb={}, doc={}", kbId, docId);

        // Cognee 同步：优先精确删除单条 data（Cognee v1.0 支持按 data_id 删除，
        // 节点+边一起消除，图的其余部分保持完整，无需重建整个 dataset）。
        // 仅当文档没有 data_id（历史数据/未入库）或精确删除失败时，才回退全量重建。
        if (cogneeDataId != null && !cogneeDataId.isBlank()) {
            if (cogneeClient.forgetData(cogneeDataId, kb.getDatasetName())) {
                log.info("[KnowledgeBase] Cognee precise deletion: doc={}, dataId={}", docId, cogneeDataId);
                return;
            }
            log.warn("[KnowledgeBase] Cognee precise deletion failed for dataId={}, fallback to dataset resync",
                    cogneeDataId);
        } else {
            log.info("[KnowledgeBase] doc={} has no cogneeDataId (legacy/unindexed), fallback to dataset resync", docId);
        }
        // 兜底重建走代理异步执行，避免阻塞删除请求（self-invocation 会让 @Async 失效）
        self.resyncKbDatasetAsync(kbId, kb.getDatasetName());
    }

    // ── 重新索引 ──────────────────────────────────────────────

    /**
     * 重新索引单个知识库：清空 Cognee dataset 后重灌全部文档（异步执行）。
     *
     * <p>用于修复历史文档向量索引缺失（后台 remember 未完成）等问题。
     * 触发后立即返回，实际重建通过 self.resyncKbDatasetAsync 在 Spring 异步线程执行，
     * 可轮询文档状态查看进度。
     *
     * <p>注意：重建必须走 self 代理调用（@Lazy self-injection），
     * 若直接调用 resyncKbDatasetAsync 属于 self-invocation，@Async 不生效，
     * 会同步阻塞请求导致前端超时。
     */
    public void reindexKb(String userId, String kbId) {
        String normalizedId = normalizeKbId(kbId);
        KnowledgeBase kb = kbRepository.findByIdAndUserId(normalizedId, userId)
                .orElseThrow(() -> new IllegalArgumentException("知识库不存在或无权访问"));
        log.info("[KnowledgeBase] Reindex KB requested: kb={}, user={}", normalizedId, userId);
        self.resyncKbDatasetAsync(normalizedId, kb.getDatasetName());
    }

    /**
     * 重新索引当前用户的所有知识库（异步逐个重建）。
     *
     * @return 触发的知识库数量
     */
    public int reindexAll(String userId) {
        List<KnowledgeBase> kbs = kbRepository.findByUserIdOrderByUpdatedAtDesc(userId);
        for (KnowledgeBase kb : kbs) {
            log.info("[KnowledgeBase] Reindex KB requested (all): kb={}, user={}", kb.getId(), userId);
            self.resyncKbDatasetAsync(kb.getId(), kb.getDatasetName());
        }
        return kbs.size();
    }

    // ── 异步操作 ──────────────────────────────────────────────

    /**
     * 异步将文档内容写入 Cognee 知识图谱。
     */
    @Async
    public void ingestToCogneeAsync(String docId, String datasetName, String content, String fileName) {
        try {
            if (content == null || content.isBlank()) {
                log.warn("[KnowledgeBase] Empty content for doc={}, skipping Cognee ingestion", docId);
                updateDocStatus(docId, KnowledgeDocument.ProcessingStatus.FAILED, "文档内容为空", null);
                return;
            }

            // 带上文件名作为上下文前缀，帮助 Cognee 实体提取
            String formattedContent = "文档名称: " + fileName + "\n\n" + content;

            // rememberWithId 返回 data_id，持久化到 doc.cogneeDataId，
            // 供后续 forgetData 精确删除（避免删除时重建整个 dataset）
            String dataId = cogneeClient.rememberWithId(formattedContent, datasetName);

            if (dataId != null) {
                updateDocStatus(docId, KnowledgeDocument.ProcessingStatus.INDEXED, null, dataId);
                log.info("[KnowledgeBase] Cognee ingestion succeeded: doc={}, dataset={}, dataId={}",
                        docId, datasetName, dataId);
            } else {
                updateDocStatus(docId, KnowledgeDocument.ProcessingStatus.FAILED,
                        "Cognee 入库返回失败", null);
                log.warn("[KnowledgeBase] Cognee ingestion returned false: doc={}", docId);
            }
        } catch (Exception e) {
            log.error("[KnowledgeBase] Cognee ingestion failed: doc={}, error={}", docId, e.getMessage(), e);
            updateDocStatus(docId, KnowledgeDocument.ProcessingStatus.FAILED, e.getMessage(), null);
        }
    }

    /**
     * 异步重建知识库的 Cognee dataset（删除单条文档兜底、或手动重新索引时调用）。
     *
     * <p>重建前把所有文档置为 PROCESSING 便于前端轮询进度，逐篇重灌成功后置 INDEXED
     * （失败置 FAILED），并刷新每篇文档的 cogneeDataId，保证之后能精确单条删除。
     */
    @Async
    public void resyncKbDatasetAsync(String kbId, String datasetName) {
        try {
            log.info("[KnowledgeBase] Resyncing dataset: kb={}, dataset={}", kbId, datasetName);

            // 清空旧 dataset
            cogneeClient.forgetDataset(datasetName);

            // 重新写入所有已入库的文档，并更新每个文档的 cogneeDataId
            List<KnowledgeDocument> docs = documentRepository.findByKbIdOrderByCreatedAtDesc(kbId);

            // 先统一标记为处理中，便于前端轮询整体进度
            for (KnowledgeDocument doc : docs) {
                doc.setStatus(KnowledgeDocument.ProcessingStatus.PROCESSING);
                doc.setErrorMessage(null);
                documentRepository.save(doc);
            }

            for (KnowledgeDocument doc : docs) {
                if (doc.getContent() != null && !doc.getContent().isBlank()) {
                    String formattedContent = "文档名称: " + doc.getFileName() + "\n\n" + doc.getContent();
                    String dataId = cogneeClient.rememberWithId(formattedContent, datasetName);
                    if (dataId != null) {
                        updateDocStatus(doc.getId(), KnowledgeDocument.ProcessingStatus.INDEXED, null, dataId);
                    } else {
                        updateDocStatus(doc.getId(), KnowledgeDocument.ProcessingStatus.FAILED,
                                "Cognee 入库返回失败", null);
                    }
                } else {
                    updateDocStatus(doc.getId(), KnowledgeDocument.ProcessingStatus.FAILED,
                            "文档内容为空", null);
                }
            }

            log.info("[KnowledgeBase] Resync complete: kb={}, docs={}", kbId, docs.size());
        } catch (Exception e) {
            log.error("[KnowledgeBase] Resync failed: kb={}, error={}", kbId, e.getMessage(), e);
        }
    }

    // ── 辅助方法 ──────────────────────────────────────────────

    private void updateDocStatus(String docId, KnowledgeDocument.ProcessingStatus status,
                                 String errorMessage, String cogneeDataId) {
        documentRepository.findById(docId).ifPresent(doc -> {
            doc.setStatus(status);
            doc.setErrorMessage(errorMessage);
            if (cogneeDataId != null) {
                doc.setCogneeDataId(cogneeDataId);
            }
            documentRepository.save(doc);
        });
    }

    /**
     * 根据知识库 ID 列表获取 Cognee dataset 名称列表。
     * 供 LongTermMemoryStage 在对话 recall 时使用。
     */
    public List<String> getDatasetNames(List<String> kbIds) {
        if (kbIds == null || kbIds.isEmpty()) {
            return List.of();
        }
        return kbRepository.findAllById(kbIds).stream()
                .map(KnowledgeBase::getDatasetName)
                .toList();
    }

    /**
     * 根据知识库 ID 列表获取知识库名称列表（用于展示"引用来源"标签）。
     */
    public List<String> getKnowledgeBaseNames(List<String> kbIds) {
        if (kbIds == null || kbIds.isEmpty()) {
            return List.of();
        }
        return kbRepository.findAllById(kbIds).stream()
                .map(KnowledgeBase::getName)
                .toList();
    }

    /**
     * 根据 Cognee 数据集名列表反查知识库名（用于 recall 结果溯源到具体知识库）。
     * 返回 datasetName → 知识库名 的映射；未命中的数据集名不包含在结果中。
     */
    public Map<String, String> getKnowledgeBaseNameByDatasets(Collection<String> datasetNames) {
        if (datasetNames == null || datasetNames.isEmpty()) {
            return Map.of();
        }
        return kbRepository.findByDatasetNameIn(datasetNames).stream()
                .collect(Collectors.toMap(KnowledgeBase::getDatasetName, KnowledgeBase::getName));
    }
}
