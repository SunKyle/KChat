package com.example.app.dto;

import com.example.app.entity.KnowledgeDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeDocumentDTO {
    private String id;
    private String kbId;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private Integer contentLength;
    private String status;
    private String errorMessage;
    private String cogneeDataId;
    private String storedFilePath;
    private String downloadUrl;
    /** Tika 提取的文本内容（供前端展示提取信息） */
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static KnowledgeDocumentDTO from(KnowledgeDocument doc) {
        return from(doc, null);
    }

    /**
     * @param doc             文档实体
     * @param downloadBaseUrl 下载链接前缀，如
     *                        /api/knowledge-bases/{kbId}/documents/{docId}/download
     */
    public static KnowledgeDocumentDTO from(KnowledgeDocument doc, String downloadBaseUrl) {
        KnowledgeDocumentDTOBuilder builder = KnowledgeDocumentDTO.builder()
                .id(doc.getId())
                .kbId(doc.getKbId())
                .fileName(doc.getFileName())
                .fileType(doc.getFileType())
                .fileSize(doc.getFileSize())
                .contentLength(doc.getContentLength())
                .status(doc.getStatus() != null ? doc.getStatus().name() : "PENDING")
                .errorMessage(doc.getErrorMessage())
                .cogneeDataId(doc.getCogneeDataId())
                .storedFilePath(doc.getStoredFilePath())
                .content(doc.getContent())
                .createdAt(doc.getCreatedAt())
                .updatedAt(doc.getUpdatedAt());
        if (downloadBaseUrl != null) {
            builder.downloadUrl(downloadBaseUrl);
        }
        return builder.build();
    }
}
