package com.example.app.repository;

import com.example.app.entity.KnowledgeDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, String> {

    /** 查询知识库下所有文档，按创建时间倒序 */
    List<KnowledgeDocument> findByKbIdOrderByCreatedAtDesc(String kbId);

    /** 按ID和知识库ID查询 */
    Optional<KnowledgeDocument> findByIdAndKbId(String id, String kbId);

    /** 删除指定知识库的文档 */
    void deleteByIdAndKbId(String id, String kbId);

    /** 统计知识库文档数量 */
    long countByKbId(String kbId);

    /** 统计知识库中已入库的文档数量 */
    long countByKbIdAndStatus(String kbId, KnowledgeDocument.ProcessingStatus status);
}
