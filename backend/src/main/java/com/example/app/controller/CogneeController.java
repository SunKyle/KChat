package com.example.app.controller;

import com.example.app.service.CogneeClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Cognee 知识图谱管理 API 控制器
 *
 * <p>
 * 暴露给前端调用的 Cognee 操作端点。核心的 remember/recall 在聊天管道内部使用，
 * 不直接暴露给前端；这里只暴露管理类操作：手动触发 improve、健康检查等。
 *
 * <h3>API 端点</h3>
 * <ul>
 * <li>POST /api/cognee/improve — 手动触发图谱自我优化（跨文档关系推理、剪枝、加权）</li>
 * <li>GET  /api/cognee/health  — 检查 Cognee 服务连通性</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/cognee")
@RequiredArgsConstructor
@Slf4j
public class CogneeController {

    private final CogneeClient cogneeClient;

    /**
     * 手动触发 Cognee 自我优化。
     *
     * <p>
     * 对应 Cognee v1.0 的 {@code improve()}：对现有图谱执行图丰富阶段，
     * 推导跨实体间接连接、重加权边、剪枝陈旧节点，使后续 recall 更准确。
     * 通常由 {@code remember(selfImprovement=true)} 自动触发，
     * 此接口用于前端显式执行（例如用户点击"图谱优化"按钮）。
     *
     * @param dataset 数据集名称，可选，默认为 main_dataset
     * @return 执行结果，success + message
     */
    @PostMapping("/improve")
    public ResponseEntity<Map<String, Object>> improveGraph(
            @RequestParam(required = false, defaultValue = "main_dataset") String dataset) {
        log.info("[CogneeController] Manual improve triggered for dataset={}", dataset);
        boolean ok = cogneeClient.improve(dataset);
        if (ok) {
            log.info("[CogneeController] improve succeeded for dataset={}", dataset);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "图谱自我优化完成"
            ));
        }
        log.warn("[CogneeController] improve failed or returned false for dataset={}", dataset);
        return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "图谱优化失败，请查看服务日志或确认 Cognee 正在运行"
        ));
    }

    /**
     * 检查 Cognee 服务是否健康。
     *
     * @return healthy 状态
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        boolean healthy = cogneeClient.isHealthy();
        return ResponseEntity.ok(Map.of(
                "healthy", healthy,
                "status", healthy ? "ok" : "unreachable"
        ));
    }

    /**
     * 获取指定知识库的知识图谱。
     *
     * @param dataset Cognee dataset 名称（如 kb_{uuid}）
     * @return 图谱节点和边
     */
    @GetMapping("/graph")
    public ResponseEntity<CogneeClient.GraphResponse> getGraph(
            @RequestParam(required = false, defaultValue = "main_dataset") String dataset) {
        CogneeClient.GraphResponse graph = cogneeClient.getGraph(dataset);
        if (graph == null) {
            return ResponseEntity.ok(new CogneeClient.GraphResponse());
        }
        return ResponseEntity.ok(graph);
    }
}
