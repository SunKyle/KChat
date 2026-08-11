package com.example.app.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * 文件服务
 *
 * 提供文件上传保存和文本解析能力。
 * 使用 Apache Tika 自动检测文件类型并提取文本，支持
 * PDF / Word / Excel / PowerPoint / HTML / TXT / Markdown / CSV / JSON 等格式。
 *
 * <p>文件存储在 {@code app.file.upload-dir} 配置的目录下（默认 {@code uploads/files}），
 * 文件名格式为 {@code {uuid}_{originalFilename}}，fileId 即 uuid 部分。
 */
@Slf4j
@Service
public class FileService {

    @Value("${app.file.upload-dir:uploads/files}")
    private String uploadDir;

    /** 解析文本的最大字符数，防止超长文档撑爆 LLM 上下文。 */
    private static final int MAX_CONTENT_CHARS = 8000;

    /** 允许上传的最大文件大小（20MB）。 */
    private static final long MAX_FILE_SIZE = 20 * 1024 * 1024;

    @PostConstruct
    public void init() {
        try {
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
                log.info("Created file upload directory: {}", uploadPath.toAbsolutePath());
            }
        } catch (IOException e) {
            log.error("Failed to create file upload directory", e);
            throw new RuntimeException("Failed to create file upload directory", e);
        }
    }

    /**
     * 上传文件并返回 fileId。
     *
     * @param file 上传的文件
     * @return fileId（UUID）
     */
    public String uploadFile(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("文件大小超过限制（最大 20MB）");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            originalFilename = "unnamed";
        }
        // 防止路径穿越：只保留文件名部分
        originalFilename = Paths.get(originalFilename).getFileName().toString();

        String fileId = UUID.randomUUID().toString().replace("-", "");
        String storedName = fileId + "_" + originalFilename;
        Path filePath = Paths.get(uploadDir, storedName);

        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        log.info("File uploaded: fileId={}, name={}, size={}bytes", fileId, originalFilename, file.getSize());

        return fileId;
    }

    /**
     * 解析文件为文本。
     *
     * @param fileId 文件 ID
     * @return 解析后的文本（最多 {@link #MAX_CONTENT_CHARS} 字符）
     */
    public String parseFile(String fileId) throws IOException {
        Path filePath = findFile(fileId);
        if (filePath == null) {
            throw new FileNotFoundException("文件不存在: " + fileId);
        }

        log.info("Parsing file: {} ({}bytes)", filePath.getFileName(), Files.size(filePath));

        // BodyContentHandler 限制提取的字符数，防止超大文档
        BodyContentHandler handler = new BodyContentHandler(MAX_CONTENT_CHARS);
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();
        AutoDetectParser parser = new AutoDetectParser();

        try (InputStream stream = Files.newInputStream(filePath)) {
            parser.parse(stream, handler, metadata, context);
        } catch (Exception e) {
            log.error("Failed to parse file: {}", fileId, e);
            throw new IOException("文件解析失败: " + e.getMessage(), e);
        }

        String text = handler.toString();
        String contentType = metadata.get("Content-Type");
        log.info("Parsed file {}: contentType={}, textLength={}", fileId, contentType, text.length());

        return text;
    }

    /**
     * 获取文件元信息（原始文件名、大小、内容类型）。
     */
    public Map<String, Object> getFileInfo(String fileId) throws IOException {
        Path filePath = findFile(fileId);
        if (filePath == null) {
            throw new FileNotFoundException("文件不存在: " + fileId);
        }

        String storedName = filePath.getFileName().toString();
        String originalName = storedName.substring(storedName.indexOf('_') + 1);
        long size = Files.size(filePath);

        // 检测内容类型
        String contentType;
        try (InputStream stream = Files.newInputStream(filePath)) {
            contentType = new AutoDetectParser().getDetector().detect(stream, new Metadata()).toString();
        } catch (Exception e) {
            contentType = "application/octet-stream";
        }

        Map<String, Object> info = new LinkedHashMap<>();
        info.put("fileId", fileId);
        info.put("fileName", originalName);
        info.put("size", size);
        info.put("contentType", contentType);
        return info;
    }

    /**
     * 列出已上传的所有文件。
     */
    public List<Map<String, Object>> listFiles() throws IOException {
        Path dir = Paths.get(uploadDir);
        if (!Files.exists(dir)) {
            return List.of();
        }

        List<Map<String, Object>> files = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        // 只包含含下划线的文件（格式: {fileId}_{originalName}）
                        int underscore = name.indexOf('_');
                        return underscore > 0 && underscore < name.length() - 1;
                    })
                    .forEach(p -> {
                        String name = p.getFileName().toString();
                        String fid = name.substring(0, name.indexOf('_'));
                        String original = name.substring(name.indexOf('_') + 1);
                        Map<String, Object> info = new LinkedHashMap<>();
                        info.put("fileId", fid);
                        info.put("fileName", original);
                        try {
                            info.put("size", Files.size(p));
                        } catch (IOException e) {
                            info.put("size", -1);
                        }
                        files.add(info);
                    });
        }
        // 按文件名排序
        files.sort(Comparator.comparing(m -> (String) m.get("fileName")));
        return files;
    }

    /**
     * 删除文件。
     */
    public void deleteFile(String fileId) throws IOException {
        Path filePath = findFile(fileId);
        if (filePath != null) {
            Files.delete(filePath);
            log.info("File deleted: fileId={}", fileId);
        }
    }

    /**
     * 根据 fileId 查找文件路径。
     * 文件名格式为 {@code {fileId}_{originalName}}，通过前缀匹配查找。
     */
    private Path findFile(String fileId) throws IOException {
        Path dir = Paths.get(uploadDir);
        if (!Files.exists(dir)) {
            return null;
        }
        String prefix = fileId + "_";
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(p -> p.getFileName().toString().startsWith(prefix))
                    .findFirst()
                    .orElse(null);
        }
    }
}
